package io.korus.web.annotaion;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequestMapping {
    String[] value() default {};
    String[] path() default {};
    RequestMethod[] method() default {};
    String[] consumes() default {};
    String[] produces() default {};
}
