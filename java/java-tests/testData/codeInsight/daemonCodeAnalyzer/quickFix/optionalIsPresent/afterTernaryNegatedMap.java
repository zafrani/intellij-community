// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  private static Optional<String> test(List<Object> list) {
    Optional<Object> first = list.stream().filter(obj -> obj instanceof String).findFirst();
    return first.map(o -> (String) o);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null, "aa", "bbb", "c", null, "dd")));
  }
}