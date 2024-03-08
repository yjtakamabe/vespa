// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.ImportedFieldsConfig;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.config.search.summary.JuniperrcConfig;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;

/**
 * Represents a document database and the backend configuration needed for this database.
 *
 * @author geirst
 */
public class DocumentDatabase extends AnyConfigProducer implements
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        AttributesConfig.Producer,
        RankProfilesConfig.Producer,
        RankingConstantsConfig.Producer,
        RankingExpressionsConfig.Producer,
        OnnxModelsConfig.Producer,
        IndexschemaConfig.Producer,
        JuniperrcConfig.Producer,
        SummaryConfig.Producer,
        ImportedFieldsConfig.Producer,
        SchemaInfoConfig.Producer,
        VsmsummaryConfig.Producer,
        VsmfieldsConfig.Producer
{

    private final String schemaName;
    private final DerivedConfiguration derivedCfg;

    public DocumentDatabase(TreeConfigProducer<AnyConfigProducer> parent, String schemaName, DerivedConfiguration derivedCfg) {
        super(parent, schemaName);
        this.schemaName = schemaName;
        this.derivedCfg = derivedCfg;
    }

    public String getName() {
        return schemaName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public DerivedConfiguration getDerivedConfiguration() {
        return derivedCfg;
    }
    // These methods will append to the config
    @Override public void getConfig(IndexInfoConfig.Builder builder) { derivedCfg.getIndexInfo().getConfig(builder); }
    @Override public void getConfig(IlscriptsConfig.Builder builder) { derivedCfg.getIndexingScript().getConfig(builder); }
    @Override public void getConfig(SchemaInfoConfig.Builder builder) { derivedCfg.getSchemaInfo().getConfig(builder); }

    // These methods append as multiple databases join config => TODO will loose information - not good
    @Override public void getConfig(AttributesConfig.Builder builder) { derivedCfg.getConfig(builder); }

    // Below methods will replace config completely
    @Override public void getConfig(OnnxModelsConfig.Builder builder) {
        builder.model(derivedCfg.getRankProfileList().getOnnxConfig());
    }
    @Override public void getConfig(RankingExpressionsConfig.Builder builder) {
        builder.expression(derivedCfg.getRankProfileList().getExpressionsConfig());
    }
    @Override public void getConfig(RankingConstantsConfig.Builder builder) {
        builder.constant(derivedCfg.getRankProfileList().getConstantsConfig());
    }
    @Override public void getConfig(RankProfilesConfig.Builder builder) {
        builder.rankprofile(derivedCfg.getRankProfileList().getRankProfilesConfig());
    }
    @Override public void getConfig(IndexschemaConfig.Builder builder) { derivedCfg.getIndexSchema().getConfig(builder); }
    @Override public void getConfig(JuniperrcConfig.Builder builder) { derivedCfg.getJuniperrc().getConfig(builder); }
    @Override public void getConfig(SummaryConfig.Builder builder) { derivedCfg.getSummaries().getConfig(builder); }
    @Override public void getConfig(ImportedFieldsConfig.Builder builder) { derivedCfg.getImportedFields().getConfig(builder); }
    @Override public void getConfig(VsmsummaryConfig.Builder builder) { derivedCfg.getVsmSummary().getConfig(builder); }
    @Override public void getConfig(VsmfieldsConfig.Builder builder) { derivedCfg.getVsmFields().getConfig(builder); }

}
