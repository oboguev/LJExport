package my.LJExport.runtime.parallel.twostage;

/**
 * Functional interface for the first (parallel) stage.
 */
@FunctionalInterface
public interface Stage1Processor<WC extends WorkContext<?>>
{
    void process(WC ctx) throws Exception;
}