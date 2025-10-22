package tardis.app;

import pt.unl.fct.di.novasys.babel.metrics.monitor.Aggregation;
import pt.unl.fct.di.novasys.babel.metrics.monitor.AggregationInput;
import pt.unl.fct.di.novasys.babel.metrics.monitor.AggregationResult;

public class NoAggregation extends Aggregation {
    private final short protocolId;
    private final String metricName;
    public  NoAggregation(short protocolId, String metricName) {
        super(protocolId, metricName);
        this.protocolId = protocolId;
        this.metricName = metricName;
    }

    @Override
    public AggregationResult aggregate(AggregationInput aggregationInput, AggregationResult aggregationResult) {
        aggregationInput.getSamplesIndexedPerHost(this.protocolId, this.metricName).forEach((host, sample) -> {
            aggregationResult.addSample(sample, protocolId, host);
        });
        return aggregationResult;
    }
}