package org.z3950.zing.cql.cql2pgjson;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class Cql2SqlUtilTest {
  @Test
  public void isUtilityClass() {
    Util.assertUtilityClass(Cql2SqlUtil.class);
  }

  /**
   * Return the first two words. Words are non-space
   * character sequences separated by spaces.
   * <p>
   * Returns "" for any of the two words if no more
   * word is found.
   * <p>
   * <pre>
   * split("foo   bar   ") = { "foo", "bar" };
   * split("  ") = { "", "" };
   * </pre>
   * @param s  string to split
   * @return two words
   */
  private String [] split(String s) {
    String [] chunks = s.split(" +");
    String left = "";
    if (chunks.length >= 1) {
      left = chunks[0];
    }
    String right = "";
    if (chunks.length >= 2) {
      right = chunks[1];
    }
    return new String [] { left, right };
  }

  private List<List<String>> params(String ... strings) {
    List<List<String>> params = new ArrayList<>();
    for (String s : strings) {
      params.add(Arrays.asList(split(s)));
    }
    return params;
  }

  public Object cql2likeParams() {
    return params(
        "           ",
        "'     ''   ",
        "a     a    ",
        "*     %    ",
        "?     _    ",
        "\\    \\\\ ",
        "\\*   \\*  ",
        "\\?   \\?  ",
        "\\%   \\%  ",
        "\\_   \\_  ",
        "\\'   ''   ",
        "\\\\  \\\\ "
        );
  }

  @Test
  @Parameters(method = "cql2likeParams")
  public void cql2like(String cql, String sql) {
    assertThat(Cql2SqlUtil.cql2like(cql), is(sql));
  }

  public Object cql2regexpParams() {
    return params(
        "           ",
        "'     ''   ",
        "a     a    ",
        "*     .*   ",
        "?     .    ",
        "^     (^|$)",
        "\\    \\\\ ",
        "\\*   \\*  ",
        "\\?   \\?  ",
        "\\^   \\^  ",
        "\\%   %    ",
        "\\_   _    ",
        "(     \\(  ",
        "\\(   \\(  ",
        "{     \\{  ",
        "\\{   \\{  ",
        "[     \\[  ",
        "\\[   \\[  ",
        "$     \\$  ",
        "\\$   \\$  ",
        "\\'   ''   ",
        "\\\\  \\\\ "
        );
  }

  @Test
  @Parameters(method = "cql2regexpParams")
  public void cql2regexp(String cql, String sql) {
    assertThat(Cql2SqlUtil.cql2regexp(cql), is(sql));
  }
}
