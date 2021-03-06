package de.cronn.reflection.util;

import static de.cronn.reflection.util.TestUtils.*;
import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.junit.Test;

import de.cronn.reflection.util.immutable.ReadOnly;
import de.cronn.reflection.util.testclasses.BaseInterface;
import de.cronn.reflection.util.testclasses.ClassWithInheritedDefaultMethods;
import de.cronn.reflection.util.testclasses.DerivedClass;
import de.cronn.reflection.util.testclasses.EntityProtectedConstructor;
import de.cronn.reflection.util.testclasses.EntityProtectedNoDefaultConstructor;
import de.cronn.reflection.util.testclasses.FindMethodByArgumentTypesTestCaseClass;
import de.cronn.reflection.util.testclasses.FindMethodByArgumentTypesTestCaseSubclass;
import de.cronn.reflection.util.testclasses.OtherClass;
import de.cronn.reflection.util.testclasses.SomeClass;
import de.cronn.reflection.util.testclasses.SomeTestInterface;
import de.cronn.reflection.util.testclasses.SubclassOfClassWithDefaultMethods;
import de.cronn.reflection.util.testclasses.TestEntity;
import javassist.util.proxy.ProxyFactory;
import net.bytebuddy.ByteBuddy;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;

public class ClassUtilsTest {

	private static final Class<?> SOME_TEST_INTERFACE_CLASS = SomeTestInterface.class;

	@Test
	public void testConstructor() throws Exception {
		assertThatConstructorIsPrivate(ClassUtils.class);
	}

	@Test
	public void testGetRealClass() throws Exception {
		assertThat(ClassUtils.getRealClass(new TestEntity())).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(createJdkProxy(SomeTestInterface.class))).isSameAs(SomeTestInterface.class);
		assertThat(ClassUtils.getRealClass(createByteBuddyProxy(new TestEntity()))).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(createJavassistProxy(new TestEntity()))).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(createCglibProxy(new TestEntity()))).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(createByteBuddyProxy(createCglibProxy(new TestEntity())))).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(createByteBuddyProxy(createJavassistProxy(new TestEntity())))).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(createByteBuddyProxy(createJavassistProxy(createCglibProxy(new TestEntity()))))).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(createCglibProxy(createByteBuddyProxy(new TestEntity())))).isSameAs(TestEntity.class);
		assertThat(ClassUtils.getRealClass(Long.valueOf(16))).isSameAs(Long.class);

		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> ClassUtils.getRealClass(createJdkProxy(SomeTestInterface.class, BaseInterface.class)))
			.withMessage("Unexpected number of interfaces: 2");
	}

	@Test
	public void testMatchesWellKnownProxyClassPattern() throws Exception {
		assertThat(ClassUtils.matchesWellKnownProxyClassNamePattern(Object.class.getName())).isFalse();
		assertThat(ClassUtils.matchesWellKnownProxyClassNamePattern(String.class.getName())).isFalse();
		assertThat(ClassUtils.matchesWellKnownProxyClassNamePattern("my.package.SomeClass")).isFalse();

		assertThat(ClassUtils.matchesWellKnownProxyClassNamePattern("my.package.SomeClass$$proxy")).isTrue();
		assertThat(ClassUtils.matchesWellKnownProxyClassNamePattern("my.package.SomeClass$ByteBuddy$abcdef")).isTrue();
		assertThat(ClassUtils.matchesWellKnownProxyClassNamePattern("my.package.SomeClass$HibernateProxy$abcdef")).isTrue();
	}

	@Test
	public void testCreateNewInstanceLikeOfProxy() throws Exception {
		Object sourceEntity = new TestEntity();
		Object proxy = createCglibProxy(sourceEntity);

		Object newInstance = ClassUtils.createNewInstanceLike(proxy);
		assertThat(newInstance.getClass()).isSameAs(TestEntity.class);
	}

	@Test
	public void testCreateNewInstanceLike_Null() throws Exception {
		Object instance = ClassUtils.createNewInstanceLike(null);
		assertThat(instance).isNull();
	}

	@Test
	public void testCreateNewInstanceLikeProtectedNoArgConstructor() throws Exception {
		Object sourceEntity = EntityProtectedConstructor.newEntity();
		Object actual = ClassUtils.createNewInstanceLike(sourceEntity);
		assertThat(actual).isInstanceOf(EntityProtectedConstructor.class);
	}

	@Test
	public void testCreateNewInstanceLikeProtectedConstructor() throws Exception {
		Object sourceEntity = EntityProtectedNoDefaultConstructor.newEntity();

		assertThatExceptionOfType(ReflectionRuntimeException.class)
			.isThrownBy(() -> ClassUtils.createNewInstanceLike(sourceEntity))
			.withCauseExactlyInstanceOf(NoSuchMethodException.class)
			.withMessage("Failed to construct an instance of " + EntityProtectedNoDefaultConstructor.class);
	}

	@Test
	public void testGetVoidMethod() throws Exception {
		Method voidMethod = ClassUtils.getVoidMethod(ClassUtilsTest.class, ClassUtilsTest::testGetVoidMethod);
		assertThat(voidMethod.getName()).isEqualTo("testGetVoidMethod");
	}

	@Test
	public void testGetVoidMethod_CallSiteSpecificLambda() throws Exception {
		VoidMethod<ClassUtilsTest> lambda = ClassUtilsTest::testGetVoidMethod;
		VoidMethod<ClassUtilsTest> callSiteSpecificLambda = lambda::invoke;

		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> ClassUtils.getVoidMethod(ClassUtilsTest.class, callSiteSpecificLambda))
			.withMessage(callSiteSpecificLambda + " is call site specific");
	}

	@Test
	public void testGetVoidMethod_lambdaWithException() throws Exception {
		assertThatExceptionOfType(ReflectionRuntimeException.class)
			.isThrownBy(() -> ClassUtils.getVoidMethod(ClassUtilsTest.class, bean -> {
				throw new IllegalStateException("some exception");
			}))
			.withRootCauseExactlyInstanceOf(IllegalStateException.class)
			.withMessage("java.lang.IllegalStateException: some exception");
	}

	@Test
	public void testGetVoidMethodName() throws Exception {
		String voidMethodName = ClassUtils.getVoidMethodName(ClassUtilsTest.class, ClassUtilsTest::testGetVoidMethod);
		assertThat(voidMethodName).isEqualTo("testGetVoidMethod");

		String methodName = ClassUtils.getVoidMethodName(new ClassUtilsTest(), ClassUtilsTest::testGetVoidMethod);
		assertThat(methodName).isEqualTo("testGetVoidMethod");

		assertThat(ClassUtils.getVoidMethodName(SomeTestInterface.class, SomeTestInterface::doOtherWork)).isEqualTo("doOtherWork");
	}

	@Test
	public void testGetVoidMethodName_AnonymousClass() throws Exception {
		SomeClass bean = new SomeClass() {
		};

		assertThatExceptionOfType(ReflectionRuntimeException.class)
			.isThrownBy(() -> ClassUtils.getVoidMethodName(bean, SomeClass::doOtherWork))
			.withMessageMatching("Failed to create proxy on class .+?")
			.withCauseExactlyInstanceOf(IllegalAccessError.class)
			.withStackTraceContaining("cannot access its superclass");
	}

	@Test
	public void testIsProxy() throws Exception {
		Object testObject = new TestEntity();
		assertThat(ClassUtils.isProxy(createJdkProxy(BaseInterface.class))).isTrue();
		assertThat(ClassUtils.isProxy(createByteBuddyProxy(testObject))).isTrue();
		assertThat(ClassUtils.isProxy(createCglibProxy(testObject))).isTrue();
		assertThat(ClassUtils.isProxy(testObject)).isFalse();
		assertThat(ClassUtils.isProxy("some string")).isFalse();
		assertThat(ClassUtils.isProxy(null)).isFalse();
	}

	@Test
	public void testIsProxyClass() throws Exception {
		Object testObject = new TestEntity();
		assertThat(ClassUtils.isProxyClass(createJdkProxy(BaseInterface.class).getClass())).isTrue();
		assertThat(ClassUtils.isProxyClass(createByteBuddyProxy(testObject).getClass())).isTrue();
		assertThat(ClassUtils.isProxyClass(createCglibProxy(testObject).getClass())).isTrue();
		assertThat(ClassUtils.isProxyClass(PropertyUtils.getCache(TestEntity.class).getMethodCapturingProxy())).isTrue();
		assertThat(ClassUtils.isProxyClass(testObject.getClass())).isFalse();
		assertThat(ClassUtils.isProxyClass(String.class)).isFalse();
		assertThat(ClassUtils.isProxyClass(null)).isFalse();
	}

	@Test
	public void testHasMethodWithSameSignature_happyPath_shouldMatchMethodSignature_whenReturnTypeAndNameAndParametersAreEqual() throws Exception {
		Method targetMethod = findMethod(SomeClass.class, "doWork", int.class);

		assertThat(ClassUtils.hasMethodWithSameSignature(SOME_TEST_INTERFACE_CLASS, targetMethod)).isTrue();
	}

	@Test
	public void testHasMethodWithSameSignature_shouldNotMatchMethodSignature_whenReturnTypeIsDifferentThanHappyPath() throws Exception {
		Method targetMethod = findMethod(OtherClass.class, "doWork", int.class);

		assertThat(ClassUtils.hasMethodWithSameSignature(SOME_TEST_INTERFACE_CLASS, targetMethod)).isFalse();
	}

	@Test
	public void testHasMethodWithSameSignature_shouldNotMatchMethodSignature_whenNameIsDifferentThanHappyPath() throws Exception {
		Method targetMethod = findMethod(SomeClass.class, "doWorkLater", int.class);

		assertThat(ClassUtils.hasMethodWithSameSignature(SOME_TEST_INTERFACE_CLASS, targetMethod)).isFalse();
	}

	@Test
	public void testHasMethodWithSameSignature_shouldNotMatchMethodSignature_whenParametersAreDifferentThanHappyPath() throws Exception {
		Method targetMethod = findMethod(SomeClass.class, "doWork", int.class, int.class);

		assertThat(ClassUtils.hasMethodWithSameSignature(SOME_TEST_INTERFACE_CLASS, targetMethod)).isFalse();
	}

	@Test
	public void testIsFromPackage() throws Exception {
		assertThat(ClassUtils.isFromPackage(ClassUtilsTest.class, "de.cronn.reflection.util")).isTrue();
		assertThat(ClassUtils.isFromPackage(ClassUtilsTest.class, "de.cronn.reflection")).isFalse();
		assertThat(ClassUtils.isFromPackage(ClassUtilsTest.class, "de.cronn")).isFalse();
		assertThat(ClassUtils.isFromPackage(ClassUtilsTest.class, "non.existing.package")).isFalse();
		assertThat(ClassUtils.isFromPackage(SomeClass.class, "de.cronn.reflection.util.testclasses")).isTrue();
	}

	@Test
	public void testFindDeclaredMethodsByArgumentTypes() throws Exception {
		assertThat(ClassUtils.findMethodsByArgumentTypes(FindMethodByArgumentTypesTestCaseClass.class, String.class, Integer.class)).hasSize(2);
		assertThat(ClassUtils.findMethodsByArgumentTypes(FindMethodByArgumentTypesTestCaseSubclass.class, String.class, Integer.class)).hasSize(3);
	}

	@Test
	public void testHaveSameSignature() throws Exception {
		Method oneMethod = SomeClass.class.getMethod("doOtherWork");
		Method otherMethod = SomeTestInterface.class.getMethod("doOtherWork");
		Method hashCodeMethod = Object.class.getMethod("hashCode");
		assertThat(ClassUtils.haveSameSignature(otherMethod, otherMethod)).isTrue();
		assertThat(ClassUtils.haveSameSignature(oneMethod, oneMethod)).isTrue();
		assertThat(ClassUtils.haveSameSignature(oneMethod, otherMethod)).isTrue();
		assertThat(ClassUtils.haveSameSignature(oneMethod, hashCodeMethod)).isFalse();
		assertThat(ClassUtils.haveSameSignature(otherMethod, hashCodeMethod)).isFalse();

		Method doWorkWithOneParameter = SomeClass.class.getMethod("doWork", int.class);
		Method doWorkWithOneParameterFromInterface = SomeTestInterface.class.getMethod("doWork", int.class);
		Method doWorkWithTwoParameters = SomeClass.class.getMethod("doWork", int.class, int.class);
		assertThat(ClassUtils.haveSameSignature(doWorkWithOneParameter, doWorkWithTwoParameters)).isFalse();
		assertThat(ClassUtils.haveSameSignature(doWorkWithOneParameter, doWorkWithOneParameterFromInterface)).isTrue();
	}

	@Test
	public void testGetAllDeclaredMethods() throws Exception {
		assertThat(withoutJacocoMethods(ClassUtils.getAllDeclaredMethods(BaseInterface.class))).hasSize(1);
		assertThat(withoutJacocoMethods(ClassUtils.getAllDeclaredMethods(SomeClass.class))).hasSize(6);
	}

	@Test
	public void testGetAllDeclaredMethodSignatures() throws Exception {
		Set<MethodSignature> methodsOfSomeClass = withoutJacocoMethodSignatures(ClassUtils.getAllDeclaredMethodSignatures(SomeClass.class));
		withoutJacocoMethodSignatures(methodsOfSomeClass);
		assertThat(mapToString(methodsOfSomeClass)).containsExactly(
			"void doOtherWork()",
			"void doWork(int)",
			"void doWork(int, int)",
			"void doWorkLater(int)"
		);

		Set<MethodSignature> methodsOfDerivedClass = withoutJacocoMethodSignatures(ClassUtils.getAllDeclaredMethodSignatures(DerivedClass.class));
		assertThat(mapToString(methodsOfDerivedClass)).containsExactly(
			"java.lang.String getBaseClassStringProperty()",
			"java.lang.Long getLongPropertyWithPackageAccessSetter()",
			"java.lang.String getOtherStringProperty()",
			"java.lang.String getSizeFromInterface()",
			"java.lang.String getStringProperty()",
			"void setBaseClassStringProperty(java.lang.String)",
			"void setLongPropertyWithPackageAccessSetter(java.lang.Long)",
			"void setStringProperty(java.lang.String)"
		);

		assertThat(withoutJacocoMethodSignatures(ClassUtils.getAllDeclaredMethodSignatures(SomeClass.class))).hasSize(4);
	}

	@Test
	public void testCreateInstance_AccessibleFlagIsRestored() throws Exception {
		Constructor<TestEntity> constructor = TestEntity.class.getDeclaredConstructor();
		assertThat(constructor.isAccessible()).isFalse();

		assertThat(ClassUtils.createInstance(constructor)).isNotNull();

		assertThat(constructor.isAccessible()).isFalse();

		constructor.setAccessible(true);

		assertThat(ClassUtils.createInstance(constructor)).isNotNull();

		assertThat(constructor.isAccessible()).isTrue();
	}

	@Test
	public void testFindAnnotation() throws Exception {
		Method getNumberMethod = ClassUtils.getVoidMethod(TestEntity.class, TestEntity::getNumber);
		assertThat(ClassUtils.findAnnotation(getNumberMethod, ReadOnly.class)).isNull();

		Method setFieldWithAnnotationOnSetter = TestEntity.class.getMethod("setFieldWithAnnotationOnSetter", String.class);
		assertThat(ClassUtils.findAnnotation(setFieldWithAnnotationOnSetter, Size.class)).isNotNull();

		Method asMyself = TestEntity.class.getMethod("asMyself");
		assertThat(ClassUtils.findAnnotation(asMyself, ReadOnly.class)).isNotNull();

		Method countNothing = TestEntity.class.getMethod("countNothing");
		assertThat(ClassUtils.findAnnotation(countNothing, ReadOnly.class)).isNotNull();

		for (Class<?> clazz : Arrays.asList(ClassWithInheritedDefaultMethods.class, SubclassOfClassWithDefaultMethods.class)) {
			Method getName = clazz.getMethod("getName");
			assertThat(ClassUtils.findAnnotation(getName, Size.class)).isNotNull();

			Method getId = clazz.getMethod("getId");
			assertThat(ClassUtils.findAnnotation(getId, NotNull.class)).isNotNull();
		}
	}

	private static Set<MethodSignature> withoutJacocoMethodSignatures(Set<MethodSignature> methodSignatures) {
		return methodSignatures.stream()
			.filter(methodSignature -> !methodSignature.getName().contains("jacoco"))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static Set<Method> withoutJacocoMethods(Set<Method> methods) {
		return methods.stream()
			.filter(method -> !method.getName().contains("jacoco"))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static List<String> mapToString(Collection<MethodSignature> methodSignatures) {
		return methodSignatures.stream()
			.map(MethodSignature::toString)
			.collect(Collectors.toList());
	}

	private static <T> T createCglibProxy(T object) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(object.getClass());
		enhancer.setCallback((FixedValue) () -> null);
		@SuppressWarnings("unchecked")
		T proxy = (T) enhancer.create();
		return proxy;
	}

	private static <T> T createJavassistProxy(T object) {
		ProxyFactory factory = new ProxyFactory();
		factory.setSuperclass(object.getClass());
		@SuppressWarnings("unchecked")
		T proxy = (T) ClassUtils.createNewInstance(factory.createClass());
		return proxy;
	}

	private static Object createJdkProxy(Class<?>... interfaces) {
		return Proxy.newProxyInstance(ClassUtilsTest.class.getClassLoader(), interfaces, (p, method, args) -> null);
	}

	private static <T> T createByteBuddyProxy(T object) {
		Class<? extends T> proxyClass = new ByteBuddy()
			.subclass(ClassUtils.getRealClass(object))
			.make()
			.load(ClassUtilsTest.class.getClassLoader())
			.getLoaded();
		return ClassUtils.createNewInstance(proxyClass);
	}

	private static Method findMethod(Class<?> aClass, String name, Class<?>... parameterTypes) throws Exception {
		return aClass.getDeclaredMethod(name, parameterTypes);
	}

}
