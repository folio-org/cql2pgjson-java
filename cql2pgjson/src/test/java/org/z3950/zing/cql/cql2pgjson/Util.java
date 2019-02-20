package org.z3950.zing.cql.cql2pgjson;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public final class Util {
  private Util() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  /**
   * Return the resource from the filePath as a String.
   * @param filePath  path to resource
   * @return resource as String
   * @throws RuntimeException if loading fails
   */
  public static String getResource(String filePath) {
    try {
      URI uri = Thread.currentThread().getContextClassLoader().getResource(filePath).toURI();
      return new String(Files.readAllBytes(Paths.get(uri)));
    } catch (IOException|URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private static void assertInvocationException(Constructor<?> constructor) {
    try {
      constructor.setAccessible(true);
      // This invocation gives 100% code coverage for the private constructor and
      // also checks that it throws the required exception.
      constructor.newInstance();
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof UnsupportedOperationException) {
        return;   // this is the required exception
      }
    } catch (Exception e) {
      throw new InternalError(e);
    }
    fail("Private constructor of utiliy class must throw UnsupportedOperationException "
        + "to fail unintended invocation via reflection.");
  }

  public static void assertUtilityClass(final Class<?> clazz) {
    try {
      assertTrue("class is final", Modifier.isFinal(clazz.getModifiers()));
      assertEquals("number of constructors", 1, clazz.getDeclaredConstructors().length);
      final Constructor<?> constructor = clazz.getDeclaredConstructor();
      assertTrue("constructor is private", Modifier.isPrivate(constructor.getModifiers()));
      assertFalse("constructor accessible", constructor.isAccessible());
      assertInvocationException(constructor);
      for (final Method method : clazz.getMethods()) {
        if (method.getDeclaringClass().equals(clazz)) {
          assertTrue("method is static - " + method, Modifier.isStatic(method.getModifiers()));
        }
      }
    } catch (Exception e) {
      throw new InternalError(e);
    }
  }
}
