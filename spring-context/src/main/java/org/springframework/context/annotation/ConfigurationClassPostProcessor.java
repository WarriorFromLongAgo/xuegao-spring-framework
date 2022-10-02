/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.PassThroughSourceExtractor;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationClassEnhancer.EnhancedConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.*;

import static org.springframework.context.annotation.AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR;

/**
 * {@link BeanFactoryPostProcessor} used for bootstrapping processing of
 * {@link Configuration @Configuration} classes.
 *
 * <p>Registered by default when using {@code <context:annotation-config/>} or
 * {@code <context:component-scan/>}. Otherwise, may be declared manually as
 * with any other BeanFactoryPostProcessor.
 *
 * <p>This post processor is priority-ordered as it is important that any
 * {@link Bean} methods declared in {@code @Configuration} classes have
 * their corresponding bean definitions registered before any other
 * {@link BeanFactoryPostProcessor} executes.
 * ConfigurationClassPostProcessor是一个BeanFactory的后置处理器，因此它的主要功能是参与BeanFactory的建造，
 * 在这个类中，会解析加了@Configuration的配置类，还会解析@ComponentScan、@ComponentScans注解扫描的包，以及解析@Import等注解。
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 3.0
 */
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor,
		PriorityOrdered, ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware {

	private static final String IMPORT_REGISTRY_BEAN_NAME =
			ConfigurationClassPostProcessor.class.getName() + ".importRegistry";


	private final Log logger = LogFactory.getLog(getClass());

	private SourceExtractor sourceExtractor = new PassThroughSourceExtractor();

	private ProblemReporter problemReporter = new FailFastProblemReporter();

	@Nullable
	private Environment environment;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

	private boolean setMetadataReaderFactoryCalled = false;

	private final Set<Integer> registriesPostProcessed = new HashSet<>();

	private final Set<Integer> factoriesPostProcessed = new HashSet<>();

	@Nullable
	private ConfigurationClassBeanDefinitionReader reader;

	private boolean localBeanNameGeneratorSet = false;

	/* Using short class names as default bean names */
	private BeanNameGenerator componentScanBeanNameGenerator = new AnnotationBeanNameGenerator();

	/* Using fully qualified class names as default bean names */
	private BeanNameGenerator importBeanNameGenerator = new AnnotationBeanNameGenerator() {
		@Override
		protected String buildDefaultBeanName(BeanDefinition definition) {
			String beanClassName = definition.getBeanClassName();
			Assert.state(beanClassName != null, "No bean class name set");
			return beanClassName;
		}
	};


	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;  // within PriorityOrdered
	}

	/**
	 * Set the {@link SourceExtractor} to use for generated bean definitions
	 * that correspond to {@link Bean} factory methods.
	 */
	public void setSourceExtractor(@Nullable SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new PassThroughSourceExtractor());
	}

	/**
	 * Set the {@link ProblemReporter} to use.
	 * <p>Used to register any problems detected with {@link Configuration} or {@link Bean}
	 * declarations. For instance, an @Bean method marked as {@code final} is illegal
	 * and would be reported as a problem. Defaults to {@link FailFastProblemReporter}.
	 */
	public void setProblemReporter(@Nullable ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	/**
	 * Set the {@link MetadataReaderFactory} to use.
	 * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
	 * {@linkplain #setBeanClassLoader bean class loader}.
	 */
	public void setMetadataReaderFactory(MetadataReaderFactory metadataReaderFactory) {
		Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
		this.metadataReaderFactory = metadataReaderFactory;
		this.setMetadataReaderFactoryCalled = true;
	}

	/**
	 * Set the {@link BeanNameGenerator} to be used when triggering component scanning
	 * from {@link Configuration} classes and when registering {@link Import}'ed
	 * configuration classes. The default is a standard {@link AnnotationBeanNameGenerator}
	 * for scanned components (compatible with the default in {@link ClassPathBeanDefinitionScanner})
	 * and a variant thereof for imported configuration classes (using unique fully-qualified
	 * class names instead of standard component overriding).
	 * <p>Note that this strategy does <em>not</em> apply to {@link Bean} methods.
	 * <p>This setter is typically only appropriate when configuring the post-processor as
	 * a standalone bean definition in XML, e.g. not using the dedicated
	 * {@code AnnotationConfig*} application contexts or the {@code
	 * <context:annotation-config>} element. Any bean name generator specified against
	 * the application context will take precedence over any value set here.
	 *
	 * @see AnnotationConfigApplicationContext#setBeanNameGenerator(BeanNameGenerator)
	 * @see AnnotationConfigUtils#CONFIGURATION_BEAN_NAME_GENERATOR
	 * @since 3.1.1
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		Assert.notNull(beanNameGenerator, "BeanNameGenerator must not be null");
		this.localBeanNameGeneratorSet = true;
		this.componentScanBeanNameGenerator = beanNameGenerator;
		this.importBeanNameGenerator = beanNameGenerator;
	}

	@Override
	public void setEnvironment(Environment environment) {
		Assert.notNull(environment, "Environment must not be null");
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.resourceLoader = resourceLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
		}
	}

	@Override
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
		if (!this.setMetadataReaderFactoryCalled) {
			this.metadataReaderFactory = new CachingMetadataReaderFactory(beanClassLoader);
		}
	}


	/**
	 * Derive further bean definitions from the configuration classes in the registry.
	 */
	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
		int registryId = System.identityHashCode(registry);
		if (this.registriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanDefinitionRegistry already called on this post-processor against " + registry);
		}
		if (this.factoriesPostProcessed.contains(registryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + registry);
		}
		this.registriesPostProcessed.add(registryId);

		processConfigBeanDefinitions(registry);
	}

	/**
	 * Prepare the Configuration classes for servicing bean requests at runtime
	 * by replacing them with CGLIB-enhanced subclasses.
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		int factoryId = System.identityHashCode(beanFactory);
		if (this.factoriesPostProcessed.contains(factoryId)) {
			throw new IllegalStateException(
					"postProcessBeanFactory already called on this post-processor against " + beanFactory);
		}
		this.factoriesPostProcessed.add(factoryId);
		if (!this.registriesPostProcessed.contains(factoryId)) {
			// BeanDefinitionRegistryPostProcessor hook apparently not supported...
			// Simply call processConfigurationClasses lazily at this point then.
			processConfigBeanDefinitions((BeanDefinitionRegistry) beanFactory);
		}

		enhanceConfigurationClasses(beanFactory);
		beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProcessor(beanFactory));
	}

	/**
	 * Build and validate a configuration model based on the registry of
	 * {@link Configuration} classes.
	 */
	public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
		// 定义一个list存放app 提供的bd（项目当中提供了@Compent）
		// BeanDefinitionHolder 是对 BeanDefinition 的包装。
		// configCandidates 用来保存所有的需要自动配置的类的 beanDefinitionHolder。
		List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
		// 获取已经注册的所有 beanDefinition 的 name。
		// 7个
		String[] candidateNames = registry.getBeanDefinitionNames();

		for (String beanName : candidateNames) {
			// 依据 beanName 从 beanFactory 中拿对应的 beanDefinition。
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
			// 判断 beanDefinition 中有没有 “org.springframework.context.annotation.ConfigurationClassPostProcessor.configurationClass” 属性？
			// (实际测试的结果是都没有这个属性)
			if (ConfigurationClassUtils.isFullConfigurationClass(beanDef) ||
					ConfigurationClassUtils.isLiteConfigurationClass(beanDef)) {
				// 果BeanDefinition中的configurationClass属性为full或者lite,则意味着已经处理过了,直接跳过
				// 这里需要结合下面的代码才能理解
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			} else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				// 判断是否是Configuration类，如果加了Configuration下面的这几个注解就不再判断了
				// beanDef 拥有 @Configuration 或 @Component 或 @ComponentScan 或 @Import 或 @ImportResource 其中的
				// 一个注解，那它就是配置类的后选择。
				// 还有  add(Component.class.getName());
				//		candidateIndicators.add(ComponentScan.class.getName());
				//		candidateIndicators.add(Import.class.getName());
				//		candidateIndicators.add(ImportResource.class.getName());
				// beanDef == appconfig

				// BeanDefinitionHolder 也可以看成一个数据结构
				// 将配置类后选者 beanDef 添加到 configCandidates 中。
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}
		//(上面测试的结果是：只有主启动类对应的 beanDefinition 添加到了configCandidates 中。)

		// Return immediately if no @Configuration classes were found
		// 如果没有配置后选择，也就是说已注册的 beanDefinition 都不需要自动配置，那么直接返回吧。
		if (configCandidates.isEmpty()) {
			return;
		}

		// 排序，根据order,不重要
		// Sort by previously determined @Order value, if applicable
		// 如果没有配置后选择，也就是说已注册的 beanDefinition 都不需要自动配置，那么直接返回吧。
		configCandidates.sort((bd1, bd2) -> {
			int i1 = ConfigurationClassUtils.getOrder(bd1.getBeanDefinition());
			int i2 = ConfigurationClassUtils.getOrder(bd2.getBeanDefinition());
			return Integer.compare(i1, i2);
		});
		// 如果BeanDefinitionRegistry是SingletonBeanRegistry子类的话,
		// 由于我们当前传入的是DefaultListableBeanFactory,是SingletonBeanRegistry 的子类
		// 因此会将registry强转为SingletonBeanRegistry
		/**
		 这里是创造一个 beanName 生成器。具体规则如下：
		 1. 先在 applicationContext 中找有没有自定义的 bean name 生成器。(默认没有自定义的)
		 2. 如果没有自定义的，那就使用默认的。
		 默认情况下：
		 - componentScanBeanNameGenerator 使用简短的类名作为 bean name。
		 - importBeanNameGenerator 使用全限定类名作为 bean name。
		 */
		// Detect any custom bean name generation strategy supplied through the enclosing application context
		SingletonBeanRegistry sbr = null;
		if (registry instanceof SingletonBeanRegistry) {
			sbr = (SingletonBeanRegistry) registry;
			if (!this.localBeanNameGeneratorSet) {
				// 是否有自定义的
				BeanNameGenerator generator = (BeanNameGenerator) sbr.getSingleton(CONFIGURATION_BEAN_NAME_GENERATOR);
				// SingletonBeanRegistry中有id为 org.springframework.context.annotation.internalConfigurationBeanNameGenerator
				// 如果有则利用他的，否则则是spring默认的
				if (generator != null) {
					this.componentScanBeanNameGenerator = generator;
					this.importBeanNameGenerator = generator;
				}
			}
		}

		// 设置环境属性。
		if (this.environment == null) {
			this.environment = new StandardEnvironment();
		}

		// 实例化ConfigurationClassParser 为了解析各个配置类
		// Parse each @Configuration class
		// 创建配置类解析器 parser，后面就是使用它 寻找 并 解析 所有的配置类。
		ConfigurationClassParser parser = new ConfigurationClassParser(
				this.metadataReaderFactory, this.problemReporter, this.environment,
				this.resourceLoader, this.componentScanBeanNameGenerator, registry);

		// 实例化2个set,candidates用于将之前加入的configCandidates进行去重
		// 因为可能有多个配置类重复了
		//  拷贝一份 configCandidates，后面要使用。里面只有一个主启动类的 beanDefinitionHolder。
		Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
		// alreadyParsed 保存已经解析到配置类。
		// alreadyParsed用于判断是否处理过
		Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
		do {
			// 很重要（扫描bean）
			// 执行解析（里面很复杂, 只解析了程序员开发的需要自动配置的类）
			/**
			 candidates 中是主配置类，因为上面有@ComponentScan、和 @EnableAutoConfiguration 所以执行了
			 基包下类的解析和 MEDA-INFO/spring.factories 文件中的 EnableAutoConfiguration 类的解析。

			 需要解析的类可以分为两种，第一种是程序员自己写的需要自动配置的；第二种是框架默认的，保存在“MEDA-INFO/spring.factories” 中。
			 程序员自己写的，parser 将类解析成 ConfigurationClass 对象，和 BeanDefinition 对象，前者保存在 parser 的
			 configurationClasses 属性中，后者 保存(注册)在 beanFactory 的 beanDefinitionMap 属性中。（算是解析完了）。
			 框架默认的，parser 只将其解析成 ConfigurationClass 对象,也保存在 parser 的
			 configurationClasses 属性中，但还没弄成 beanDefinition。（解析未完成）。
			 */
			parser.parse(candidates);
			// 每个需要自动配置的 class 会被解析成 ConfigurationClass 对象，并保存在 parser 中，
			// 这里是校验所有解析到的 ConfigurationClass 对象。（至于怎么校验的，忽略它吧）
			parser.validate();

			// 将 parser 解析到的所有 ConfigurationClass 对象拷贝一份到 configClasses，后面要用。
			Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
			// 第一次循环，alreadyParsed 是空的，但如果有第二次、第三次循环 alreadyParsed 就不为空了。
			configClasses.removeAll(alreadyParsed);

			// Read the model and create bean definitions based on its content
			// 上面说道过，configClasses 中一部分已经有了对应的 beanDefinition，一部分还没有。
			// 如果要为没有的那部分新建 beanDefinition，那得先创建个读对象。
			if (this.reader == null) {
				// (看这个名字，都能感觉到它跟创建 BeanDefinition 有关。)
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}
			/**
			 * 这里值得注意的是扫描出来的bean当中可能包含了特殊类
			 * 比如ImportBeanDefinitionRegistrar那么也在这个方法里面处理
			 * 但是并不是包含在configClasses当中
			 * configClasses当中主要包含的是importSelector
			 * 因为ImportBeanDefinitionRegistrar在扫描出来的时候已经被添加到一个list当中去了
			 *
			 * // 给 configClasses 中的元素载入 beanDefinition。如果元素有 beanDefinition,那跳过,如果没有就新建。
			 */
			//bd 到 map 除却普通 （将import的bean注册到bean工厂）（重要）
			this.reader.loadBeanDefinitions(configClasses);
			// 到这里，configClasses 中的元素都已经解析完成了。
			alreadyParsed.addAll(configClasses);
			// 配置类候选者被解析完了，要清空的，否则后面再添加就说不清楚了。
			candidates.clear();
			//  只要往 beanDifinitionMap 中添加了新的 beanDefinition，if 判断绝对是成立的。
			// 由于我们这里进行了扫描，把扫描出来的BeanDefinition注册给了factory
			if (registry.getBeanDefinitionCount() > candidateNames.length) {
				// newCandidateNames:此时 beanDifinitionMap 所有 beanDefinition 的 name。
				String[] newCandidateNames = registry.getBeanDefinitionNames();
				// oldCandidateNames:执行解析前 beanDifinitionMap 所有 beanDefinition 的 name。
				Set<String> oldCandidateNames = new HashSet<>(Arrays.asList(candidateNames));
				// alreadyParsedClasses: 保存刚才已经完成解析的所有的 configurationClass 的 name。
				Set<String> alreadyParsedClasses = new HashSet<>();
				// 遍历 alreadyParsed，拿出每个元素的 name 保存到 alreadyParsedClasses。
				for (ConfigurationClass configurationClass : alreadyParsed) {
					alreadyParsedClasses.add(configurationClass.getMetadata().getClassName());
				}
				// (真正的检查遗漏要开始了)
				// 遍历全部的 beanDefinition 的 name。
				for (String candidateName : newCandidateNames) {
					// candidateName 对应的 beanDefinition 不是最初的。
					if (!oldCandidateNames.contains(candidateName)) {
						// 将这个 beanDefinition 拿出来。
						BeanDefinition bd = registry.getBeanDefinition(candidateName);
						// 再判断：（bd 满足配置类条件，但还没有被解析？）
						if (ConfigurationClassUtils.checkConfigurationClassCandidate(bd, this.metadataReaderFactory) &&
								!alreadyParsedClasses.contains(bd.getBeanClassName())) {
							// 如果真是这样，那 bd 就是被遗漏的 beanDefinition 了。要循环一次再将其解析。
							candidates.add(new BeanDefinitionHolder(bd, candidateName));
						}
					}
				}
				// 无论循环与否，这个都应该更新。
				candidateNames = newCandidateNames;
			}
		}
		while (!candidates.isEmpty());

		// 这里是直接给 beanFactory 中的 singletonObject 属性添加了一个 bean。
		// Register the ImportRegistry as a bean in order to support ImportAware @Configuration classes
		if (sbr != null && !sbr.containsSingleton(IMPORT_REGISTRY_BEAN_NAME)) {
			sbr.registerSingleton(IMPORT_REGISTRY_BEAN_NAME, parser.getImportRegistry());
		}
		// 解析完了，清缓存。
		if (this.metadataReaderFactory instanceof CachingMetadataReaderFactory) {
			// Clear cache in externally provided MetadataReaderFactory; this is a no-op
			// for a shared cache since it'll be cleared by the ApplicationContext.
			((CachingMetadataReaderFactory) this.metadataReaderFactory).clearCache();
		}
	}

	/**
	 * Post-processes a BeanFactory in search of Configuration class BeanDefinitions;
	 * any candidates are then enhanced by a {@link ConfigurationClassEnhancer}.
	 * Candidate status is determined by BeanDefinition attribute metadata.
	 *
	 * @see ConfigurationClassEnhancer
	 */
	public void enhanceConfigurationClasses(ConfigurableListableBeanFactory beanFactory) {
		Map<String, AbstractBeanDefinition> configBeanDefs = new LinkedHashMap<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			if (ConfigurationClassUtils.isFullConfigurationClass(beanDef)) {
				if (!(beanDef instanceof AbstractBeanDefinition)) {
					throw new BeanDefinitionStoreException("Cannot enhance @Configuration bean definition '" +
							beanName + "' since it is not stored in an AbstractBeanDefinition subclass");
				} else if (logger.isInfoEnabled() && beanFactory.containsSingleton(beanName)) {
					logger.info("Cannot enhance @Configuration bean definition '" + beanName +
							"' since its singleton instance has been created too early. The typical cause " +
							"is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor " +
							"return type: Consider declaring such methods as 'static'.");
				}
				configBeanDefs.put(beanName, (AbstractBeanDefinition) beanDef);
			}
		}
		if (configBeanDefs.isEmpty()) {
			// nothing to enhance -> return immediately
			return;
		}

		ConfigurationClassEnhancer enhancer = new ConfigurationClassEnhancer();
		for (Map.Entry<String, AbstractBeanDefinition> entry : configBeanDefs.entrySet()) {
			AbstractBeanDefinition beanDef = entry.getValue();
			// If a @Configuration class gets proxied, always proxy the target class
			beanDef.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
			try {
				// Set enhanced subclass of the user-specified bean class
				Class<?> configClass = beanDef.resolveBeanClass(this.beanClassLoader);
				if (configClass != null) {
					Class<?> enhancedClass = enhancer.enhance(configClass, this.beanClassLoader);
					if (configClass != enhancedClass) {
						if (logger.isTraceEnabled()) {
							logger.trace(String.format("Replacing bean definition '%s' existing class '%s' with " +
									"enhanced class '%s'", entry.getKey(), configClass.getName(), enhancedClass.getName()));
						}
						beanDef.setBeanClass(enhancedClass);
					}
				}
			} catch (Throwable ex) {
				throw new IllegalStateException("Cannot load configuration class: " + beanDef.getBeanClassName(), ex);
			}
		}
	}


	private static class ImportAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {

		private final BeanFactory beanFactory;

		public ImportAwareBeanPostProcessor(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public PropertyValues postProcessProperties(@Nullable PropertyValues pvs, Object bean, String beanName) {
			// Inject the BeanFactory before AutowiredAnnotationBeanPostProcessor's
			// postProcessProperties method attempts to autowire other configuration beans.
			if (bean instanceof EnhancedConfiguration) {
				((EnhancedConfiguration) bean).setBeanFactory(this.beanFactory);
			}
			return pvs;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			if (bean instanceof ImportAware) {
				ImportRegistry ir = this.beanFactory.getBean(IMPORT_REGISTRY_BEAN_NAME, ImportRegistry.class);
				AnnotationMetadata importingClass = ir.getImportingClassFor(bean.getClass().getSuperclass().getName());
				if (importingClass != null) {
					((ImportAware) bean).setImportMetadata(importingClass);
				}
			}
			return bean;
		}
	}

}
