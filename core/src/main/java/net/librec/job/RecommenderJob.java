/**
 * Copyright (C) 2016 LibRec
 * <p>
 * This file is part of LibRec.
 * LibRec is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * LibRec is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with LibRec. If not, see <http://www.gnu.org/licenses/>.
 */
package net.librec.job;

import net.librec.common.LibrecException;
import net.librec.conf.Configuration;
import net.librec.data.DataModel;
import net.librec.data.splitter.KCVDataSplitter;
import net.librec.data.splitter.LOOCVDataSplitter;
import net.librec.eval.Measure.MeasureValue;
import net.librec.eval.RecommenderEvaluator;
import net.librec.filter.RecommendedFilter;
import net.librec.math.algorithm.Randoms;
import net.librec.recommender.Recommender;
import net.librec.recommender.RecommenderContext;
import net.librec.recommender.item.RecommendedItem;
import net.librec.similarity.RecommenderSimilarity;
import net.librec.util.DriverClassUtil;
import net.librec.util.FileUtil;
import net.librec.util.JobUtil;
import net.librec.util.ReflectionUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * RecommenderJob
 *
 * @author WangYuFeng
 */
public class RecommenderJob {
    /**
     * LOG
     */
    protected final Log LOG = LogFactory.getLog(this.getClass());

    private Configuration conf;

    private DataModel dataModel;

    public RecommenderJob(Configuration conf) {
        this.conf = conf;
        boolean uniform = conf.getBoolean("data.splitter.random.uniform");
        if (uniform) {
            Randoms.uniform(uniform);
        }
        setJobId(JobUtil.generateNewJobId());
    }

    private void setJobId(String jobId) {
        conf.set("rec.job.id", jobId);
    }

    public void setRecommenderClass(String jobClass) {
        conf.set("rec.recommender.class", jobClass);
    }

    public void setRecommenderClass(Class<Recommender> jobClass) {
        conf.set("rec.recommender.class", jobClass.getName());
    }

    /**
     * Get data model class.
     *
     * @return {@code Class<? extends DataModel>} object
     * @throws ClassNotFoundException
     *             if the class is not found
     * @throws IOException
     *             If an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    public Class<? extends DataModel> getDataModelClass() throws ClassNotFoundException, IOException {
        return (Class<? extends DataModel>) DriverClassUtil.getClass(conf.get("data.model.format"));
    }

    /**
     * Get similarity class
     *
     * @return similarity class object
     */
    @SuppressWarnings("unchecked")
    public Class<? extends RecommenderSimilarity> getSimilarityClass() {
        try {
            return (Class<? extends RecommenderSimilarity>) DriverClassUtil.getClass(conf.get("rec.similarity.class"));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Get recommender class. {@code Recommender}.
     *
     * @return recommender class object
     * @throws ClassNotFoundException
     *             if can't find the class of recommender
     * @throws IOException
     *             If an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    public Class<? extends Recommender> getRecommenderClass() throws ClassNotFoundException, IOException {
        return (Class<? extends Recommender>) DriverClassUtil.getClass(conf.get("rec.recommender.class"));
    }

    /**
     * Get evaluator class. {@code RecommenderEvaluator}.
     *
     * @return evaluator class object
     * @throws ClassNotFoundException
     *             if can't find the class of evaluator
     * @throws IOException
     *             If an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    public Class<? extends RecommenderEvaluator> getEvaluatorClass() throws ClassNotFoundException, IOException {
        return (Class<? extends RecommenderEvaluator>) DriverClassUtil.getClass(conf.get("rec.eval.class"));
    }

    /**
     * Get filter class. {@code RecommendedFilter}.
     * 
     * @return evaluator class object
     * @throws ClassNotFoundException
     *             if can't find the class of filter
     * @throws IOException
     *             If an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    public Class<? extends RecommendedFilter> getFilterClass() throws ClassNotFoundException, IOException {
        return (Class<? extends RecommendedFilter>) DriverClassUtil.getClass(conf.get("rec.filter.class"));
    }

    /**
     * run Job
     * 
     * @throws LibrecException
     *             If an LibrecException error occurs.
     * @throws ClassNotFoundException
     *             if can't find the class of filter
     * @throws IOException
     *             If an I/O error occurs.
     */
    public void runJob() throws LibrecException, ClassNotFoundException, IOException {
        String modelSplit = conf.get("data.model.splitter");
        switch (modelSplit) {
        case "kcv": {
            int cvNumber = conf.getInt("data.splitter.cv.number", 1);
            for (int i = 1; i <= cvNumber; i++) {
                LOG.info("Splitter info: the index of " + modelSplit + " splitter times is " + i);
                conf.set("data.splitter.cv.index", String.valueOf(i));
                executeRecommenderJob();
            }
            break;
        }
        case "loocv": {
            int cvNumber = conf.getInt("data.splitter.cv.number", 1);
            for (int i = 0; i < cvNumber; i++) {
                LOG.info("Splitter info: the index of " + modelSplit + " splitter times is " + i);
                conf.set("data.splitter.cv.index", String.valueOf(i));
                executeRecommenderJob();
            }
            break;
        }
        case "given": {
            executeRecommenderJob();
            break;
        }
        case "ratio": {
            executeRecommenderJob();
            break;
        }
        }
    }

    /**
     * execute Recommender Job
     * 
     * @throws LibrecException
     *             If an LibrecException error occurs.
     * @throws ClassNotFoundException
     *             if can't find the class of filter
     * @throws IOException
     *             If an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    private void executeRecommenderJob() throws ClassNotFoundException, LibrecException, IOException {
        generateDataModel();
        RecommenderContext context = new RecommenderContext(conf, dataModel);
        generateSimilarity(context);
        Recommender recommender = (Recommender) ReflectionUtil.newInstance((Class<Recommender>) getRecommenderClass(), conf);
        recommender.recommend(context);
        executeEvaluator(recommender);
        List<RecommendedItem> recommendedList = recommender.getRecommendedList();
        recommendedList = filterResult(recommendedList);
        saveResult(recommendedList);
    }

    /**
     * Generate data model.
     * 
     * @throws ClassNotFoundException
     * @throws IOException
     * @throws LibrecException
     */
    @SuppressWarnings("unchecked")
    private void generateDataModel() throws ClassNotFoundException, IOException, LibrecException {
        if (null == dataModel) {
            dataModel = ReflectionUtil.newInstance((Class<DataModel>) this.getDataModelClass(), conf);
        }
        dataModel.buildDataModel();
    }

    /**
     * Generate similarity.
     * 
     * @param context recommender context
     */
    private void generateSimilarity(RecommenderContext context) {
        String[] similarityKeys = conf.getStrings("rec.recommender.similarities");
        if (similarityKeys != null && similarityKeys.length > 0) {
            for(int i = 0; i< similarityKeys.length; i++){
                if (getSimilarityClass() != null) {
                    RecommenderSimilarity similarity = (RecommenderSimilarity) ReflectionUtil.newInstance(getSimilarityClass(), conf);
                    conf.set("rec.recommender.similarity.key", similarityKeys[i]);
                    similarity.buildSimilarityMatrix(dataModel);
                    if(i == 0){
                        context.setSimilarity(similarity);
                    }
                    context.addSimilarities(similarityKeys[i], similarity);
                }
            }
        }
    }

    /**
     * Filter the results.
     *
     * @param recommendedList  list of recommended items
     * @return recommended List
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private List<RecommendedItem> filterResult(List<RecommendedItem> recommendedList) throws ClassNotFoundException, IOException {
        if (getFilterClass() != null) {
            RecommendedFilter filter = (RecommendedFilter) ReflectionUtil.newInstance(getFilterClass(), null);
            recommendedList = filter.filter(recommendedList);
        }
        return recommendedList;
    }

    /**
     * Execute evaluator.
     *
     * @param recommender  recommender algorithm
     * @throws LibrecException        if error occurs
     * @throws IOException            if I/O error occurs
     * @throws ClassNotFoundException if class not found error occurs
     */
    private void executeEvaluator(Recommender recommender) throws ClassNotFoundException, IOException, LibrecException {
        if (conf.getBoolean("rec.eval.enable")) {
            if (getEvaluatorClass() != null) {// Run the evaluator which is
                // designated.
                RecommenderEvaluator evaluator = (RecommenderEvaluator) ReflectionUtil.newInstance(getEvaluatorClass(), null);
                double evalValue = recommender.evaluate(evaluator);
                LOG.info("Evaluator info:" + evaluator.getClass().getSimpleName() + " is " + evalValue);
            } else {// Run all evaluators
                Map<MeasureValue, Double> evalValueMap = recommender.evaluateMap();
                if (evalValueMap != null && evalValueMap.size() > 0) {
                    for (Map.Entry<MeasureValue, Double> entry : evalValueMap.entrySet()) {
                        if (entry != null && entry.getKey() != null) {
                            if (entry.getKey().getTopN() != null && entry.getKey().getTopN() > 0) {
                                LOG.info("Evaluator value:" + entry.getKey().getMeasure() + " top " + entry.getKey().getTopN() + " is " + entry.getValue());
                            } else {
                                LOG.info("Evaluator value:" + entry.getKey().getMeasure() + " is " + entry.getValue());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Save result.
     *
     * @param recommendedList         list of recommended items
     * @throws LibrecException        if error occurs
     * @throws IOException            if I/O error occurs
     * @throws ClassNotFoundException if class not found error occurs
     */
    public void saveResult(List<RecommendedItem> recommendedList) throws LibrecException, IOException, ClassNotFoundException {
        if (recommendedList != null && recommendedList.size() > 0) {
            // make output path
            String algoSimpleName = DriverClassUtil.getDriverName(getRecommenderClass());
            String outputPath = conf.get("dfs.result.dir") + "/" + conf.get("data.input.path") + "-" + algoSimpleName + "-output/" + algoSimpleName;
            if ((dataModel.getDataSplitter() instanceof KCVDataSplitter || dataModel.getDataSplitter() instanceof LOOCVDataSplitter) && null != conf.getInt("data.splitter.cv.index")) {
                outputPath = outputPath + "-" + String.valueOf(conf.getInt("data.splitter.cv.index"));
            }
            LOG.info("Result path is " + outputPath);
            // convert itemList to string
            StringBuilder sb = new StringBuilder();
            for (RecommendedItem recItem : recommendedList) {
                String userId = recItem.getUserId();
                String itemId = recItem.getItemId();
                String value = String.valueOf(recItem.getValue());
                sb.append(userId).append(",").append(itemId).append(",").append(value).append("\n");
            }
            String resultData = sb.toString();
            // save resultData
            try {
                FileUtil.writeString(outputPath, resultData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
