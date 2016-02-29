import com.sun.btrace.BTraceUtils;
import com.sun.btrace.BTraceUtils.*;
import com.sun.btrace.Profiler;
import com.sun.btrace.annotations.*;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.AnyType;

import java.io.File;
import java.util.Deque;

import static com.sun.btrace.BTraceUtils.*;

@BTrace
public class GradleCacheAccess {
    @Property(name = "GradleCacheAccess")
    public static Profiler profiler = Profiling.newProfiler();

    @TLS
    public static Deque<Long> cacheUsages = Collections.newDeque();

    @OnMethod(
            clazz = "org.gradle.cache.internal.DefaultCacheAccess",
            method = "useCache"
    )
    public static void entry(AnyType[] args) {
        Collections.push(cacheUsages, box(timeNanos()));
    }

    @OnMethod(
            clazz = "org.gradle.cache.internal.DefaultCacheAccess",
            method = "useCache",
            location = @Location(Kind.RETURN)
    )
    public static void exit(@Self Object thisObject) {
        long startTime = unbox(Collections.removeFirst(cacheUsages));
        long duration = timeNanos() - startTime;
        File dir = (File)Reflective.get(Reflective.field(classOf(thisObject), "baseDir"), thisObject);
        String displayName = Strings.str(dir);
        Profiling.recordEntry(profiler, displayName);
        Profiling.recordExit(profiler, displayName, duration);
    }

    @OnMethod(clazz = "org.gradle.launcher.exec.InProcessBuildActionExecuter$DefaultBuildController",
            method = "run",
            location = @Location(Kind.RETURN))
    public static void reset() {
        BTraceUtils.println("--------------------------------------------------");
        Profiling.printSnapshot("Cache Access", profiler, "%1$s %2$s %8$s %9$s %10$s");
        BTraceUtils.println("--------------------------------------------------");
        Profiling.reset(profiler);
    }
}
