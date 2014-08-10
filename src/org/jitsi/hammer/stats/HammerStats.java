/*
 * Jitsi-Hammer, A traffic generator for Jitsi Videobridge.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.hammer.stats;


import java.io.*;
import java.text.*;
import java.util.*;

import org.apache.commons.math3.stat.descriptive.*;
import org.jitsi.hammer.*;
import org.jitsi.service.neomedia.MediaStreamStats;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.util.Logger;

/**
 * @author Thomas Kuntz
 *
 * This class is used to keep track of stats of all the streams of all the
 * fake users (<tt>FakeUser</tt>), generate new new stats, writes stats
 * to files, print them etc...
 *
 */
public class HammerStats implements Runnable
{
    /**
     * The <tt>Logger</tt> used by the <tt>HammerStats</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(HammerStats.class);

    /**
     * A boolean used to stop the run method of this <tt>HammerStats</tt>.
     */
    private boolean threadStop = false;

    /**
     * The name (not the path or location) of the directory where
     * the stats files will be written.
     */
    private static final String STATS_DIR_NAME = "stats";

    /**
     * The path to the stats directory. All stats will be written in files
     * located in this directory.
     */
    private final String statsDirectoryPath;

    /**
     * The file that will contain the overall stats
     */
    private final File overallStatsFile;

    /**
     * The file that will contain all the stats recorded by run()
     */
    private final File allStatsFile;

    /**
     * An <tt>List</tt> of <tt>FakeUserStats</tt> that contains the
     * <tt>MediaStreamStats</tt>s of the <tt>FakeUser</tt>.
     * It is used to keep track of the streams' stats.
     */
    private final ArrayList<FakeUserStats> fakeUserStatsList =
        new ArrayList<FakeUserStats>();

    /**
     * The time (in seconds) the HammerStats wait between two updates.
     */
    private int timeBetweenUpdate = 5;

    /**
     * The boolean used to know if the logging of all the stats in the
     * run method is enable.
     */
    private boolean allStatsLogging = false;

    /**
     * The boolean used to know if the logging of the summary stats
     * (like mean, standard deviation, min, max...) computed at each polling from
     * all the streams' stats is enable of not.
     */
    private boolean summaryStatsLogging = false;

    /**
     * The boolean used to know if the logging of the overall stats
     * (like mean, standard deviation, min, max...) computed from
     * all the streams' stats collected is enable of not.
     */
    private boolean overallStatsLogging;
    /**
     * The HammerSummaryStats used to compute summary stats from the
     * audio streams' stats.
     */
    HammerSummaryStats audioSummaryStats = new HammerSummaryStats();

    /**
     * The HammerSummaryStats used to compute summary stats from the
     * video streams' stats.
     */
    HammerSummaryStats videoSummaryStats = new HammerSummaryStats();


    /**
     * Initialize an instance of a <tt>HammerStats</tt> with the default
     * stats directory path.
     */
    public HammerStats()
    {
        this( System.getProperty(Main.PNAME_SC_HOME_DIR_LOCATION)
            + File.separator
            + System.getProperty(Main.PNAME_SC_HOME_DIR_NAME)
            + File.separator
            + HammerStats.STATS_DIR_NAME);
    }

    /**
     * Initialize an instance of a <tt>HammerStats</tt> with a custom stats
     * directory path.
     * @param statsDirectoryPath the path to the stats directory, where the
     * stats files will be saved.
     */
    public HammerStats(String statsDirectoryPath)
    {
        this.statsDirectoryPath =
            statsDirectoryPath
            + File.separator
            + new SimpleDateFormat("yyyy-MM-dd'  'HH'h'mm'm'ss's'").format(new Date());

        this.overallStatsFile = new File(
            this.statsDirectoryPath
            + File.separator
            + "overallStats.json");
        this.allStatsFile = new File(
            this.statsDirectoryPath
            + File.separator
            + "AllAndSummaryStats.json");

        logger.info("Stats directory : " + this.statsDirectoryPath);
    }


    public synchronized void addFakeUsersStats(
        FakeUserStats fakeUserStats)
    {
        if(fakeUserStats == null)
        {
            throw new NullPointerException("FakeUserStats can't be null");
        }
        fakeUserStatsList.add(fakeUserStats);
    }

    /**
     * Keep track, collect and update the stats of all the
     * <tt>MediaStreamStats</tt> this <tt>HammerStats</tt> handles.
     *
     * Also write the results in the stats files.
     */
    public void run()
    {
        PrintWriter writer = null;
        StringBuilder allBldr = new StringBuilder();
        String delim;
        String delim_ = "";
        synchronized(this)
        {
            threadStop = false;
        }

        logger.info("Running the main loop");
        while(threadStop == false)
        {
            synchronized(this)
            {
                if(overallStatsLogging || allStatsLogging || summaryStatsLogging)
                {
                    if(allStatsLogging || summaryStatsLogging)
                    {
                        if(writer == null)
                        {
                            try
                            {
                                writer = new PrintWriter(allStatsFile, "UTF-8");
                                writer.print("[\n");
                            }
                            catch (FileNotFoundException e)
                            {
                                logger.fatal("HammerStats stopping due to FileNotFound",e);
                                stop();
                            }
                            catch (UnsupportedEncodingException e)
                            {
                                logger.fatal("HammerStats stopping due to "
                                    + "UnsupportedEncoding", e);
                            }
                        }

                        //Clear the StringBuilder
                        allBldr.setLength(0);

                        writer.print(delim_ + '\n');
                        delim_ = ",";
                        writer.print("{\n");
                        writer.print("  \"timestamp\":" + System.currentTimeMillis()+",\n");
                    }

                    delim = "";
                    logger.info("Updating the MediaStreamStats");
                    for(FakeUserStats stats : fakeUserStatsList)
                    {
                        //We update the stats before using/reading them.
                        stats.updateStats();
                    }

                    for(FakeUserStats stats : fakeUserStatsList)
                    {
                        if(allStatsLogging)
                        {
                            allBldr.append(delim + stats.getStatsJSON(2) + '\n');
                            delim = ",";
                        }

                        if(summaryStatsLogging || overallStatsLogging)
                        {
                            logger.info("Adding stats values from the"
                                + " MediaStreamStats to their"
                                + " HammerSummaryStats objects");
                            audioSummaryStats.add(
                                stats.getMediaStreamStats(MediaType.AUDIO));
                            videoSummaryStats.add(
                                stats.getMediaStreamStats(MediaType.VIDEO));
                        }
                    }

                    if(allStatsLogging)
                    {
                        logger.info("Writing all stats to file");
                        writer.print("  \"users\":\n");
                        writer.print("  [\n");
                        writer.print(allBldr.toString());
                        writer.print("  ]");
                        if(summaryStatsLogging) writer.print(',');
                        writer.print('\n');
                    }
                    if(summaryStatsLogging)
                    {
                        logger.info("Writing summary stats to file");
                        writer.print("  \"summary\":\n");
                        writer.print("  {\n");


                        writer.print("    \"max\":\n");
                        writer.print("    {\n");
                        writer.print("        \"audio\":");
                        writer.print(audioSummaryStats.getMaxJSON() + ",\n");
                        writer.print("        \"video\":");
                        writer.print(videoSummaryStats.getMaxJSON() + '\n');
                        writer.print("    },\n");

                        writer.print("    \"mean\":\n");
                        writer.print("    {\n");
                        writer.print("       \"audio\":");
                        writer.print(audioSummaryStats.getMeanJSON() + ",\n");
                        writer.print("        \"video\":");
                        writer.print(videoSummaryStats.getMeanJSON() + '\n');
                        writer.print("    },\n");

                        writer.print("    \"min\":\n");
                        writer.print("    {\n");
                        writer.print("        \"audio\":");
                        writer.print(audioSummaryStats.getMinJSON() + ",\n");
                        writer.print("        \"video\":");
                        writer.print(videoSummaryStats.getMinJSON() + '\n');
                        writer.print("    },\n");

                        writer.print("    \"standard_deviation\":\n");
                        writer.print("    {\n");
                        writer.print("        \"audio\":");
                        writer.print(audioSummaryStats.getStandardDeviationJSON() + ",\n");
                        writer.print("        \"video\":");
                        writer.print(videoSummaryStats.getStandardDeviationJSON() + '\n');
                        writer.print("    }\n");


                        writer.print("  }\n");
                    }
                    if(allStatsLogging || summaryStatsLogging)
                    {
                        writer.append("}");
                        writer.flush();
                    }
                }

                if(summaryStatsLogging || overallStatsLogging)
                {
                    logger.info("Clearing the HammerSummaryStats by creating new"
                        + " SummaryStats objects for each watched stats");
                    audioSummaryStats.clear();
                    videoSummaryStats.clear();
                }
            }

            try
            {
                Thread.sleep(timeBetweenUpdate * 1000);
            }
            catch (InterruptedException e)
            {
                logger.fatal("Error during sleep in main loop : " + e);
                stop();
            }
        }
        logger.info("Exiting the main loop");

        if(writer != null)
        {
            writer.print("]\n");
            writer.close();
        }

        if(overallStatsLogging) writeOverallStats();
    }

    /**
     * Provoke the stop of the method run(). If the method run() is not running,
     * calling this method won't do anything
     */
    public synchronized void stop()
    {
        logger.info("Stopping the main loop");
        threadStop = true;
    }

    /**
     * Write the overall stats of the <tt>MediaStream</tt> this
     * <tt>MediaStreamStats</tt> keep track in its file.
     */
    public void writeOverallStats()
    {
        try
        {
            logger.info("Writting overall stats to file");
            PrintWriter writer = new PrintWriter(overallStatsFile, "UTF-8");
            writer.print(getOverallStats() + '\n');
            writer.close();
        }
        catch (FileNotFoundException e)
        {
            logger.fatal("Overall stats file opening error",e);
        }
        catch (UnsupportedEncodingException e)
        {
            logger.fatal("Overall stats file opening error",e);
        }
    }

    /**
     * print the overall stats of the <tt>MediaStream</tt> this
     * <tt>MediaStreamStats</tt> keep track to the PrintStream given as argument.
     * @param ps the <tt>PrintStream</tt> used to print the stats
     */
    public void printOverallStats(PrintStream ps)
    {
        ps.println(getOverallStats());
    }

    /**
     * Create and return the String that contains the overall stats.
     * @return the String that contains the overall stats.
     */
    protected String getOverallStats()
    {
        StringBuilder bldr = new StringBuilder();
        bldr.append("{\n");


        bldr.append("  \"max\":\n");
        bldr.append("  {\n");
        bldr.append("      \"audio\":");
        bldr.append(audioSummaryStats.getAggregateMaxJSON() + ",\n");
        bldr.append("      \"video\":");
        bldr.append(videoSummaryStats.getAggregateMaxJSON() + '\n');
        bldr.append("  },\n");

        bldr.append("  \"mean\":\n");
        bldr.append("  {\n");
        bldr.append("     \"audio\":");
        bldr.append(audioSummaryStats.getAggregateMeanJSON() + ",\n");
        bldr.append("      \"video\":");
        bldr.append(videoSummaryStats.getAggregateMeanJSON() + '\n');
        bldr.append("  },\n");

        bldr.append("  \"min\":\n");
        bldr.append("  {\n");
        bldr.append("      \"audio\":");
        bldr.append(audioSummaryStats.getAggregateMinJSON() + ",\n");
        bldr.append("      \"video\":");
        bldr.append(videoSummaryStats.getAggregateMinJSON() + '\n');
        bldr.append("  },\n");

        bldr.append("  \"standard_deviation\":\n");
        bldr.append("  {\n");
        bldr.append("      \"audio\":");
        bldr.append(audioSummaryStats.getAggregateStandardDeviationJSON() + ",\n");
        bldr.append("      \"video\":");
        bldr.append(videoSummaryStats.getAggregateStandardDeviationJSON() + '\n');
        bldr.append("  },\n");

        bldr.append("  \"sum\":\n");
        bldr.append("  {\n");
        bldr.append("      \"audio\":");
        bldr.append(audioSummaryStats.getAggregateSumJSON() + ",\n");
        bldr.append("      \"video\":");
        bldr.append(videoSummaryStats.getAggregateSumJSON() + '\n');
        bldr.append("  }\n");


        bldr.append("}\n");
        return bldr.toString();
    }

    /**
     * Set the time this <tt>HammerStats</tt> will wait between 2 updates of
     * stats.
     * @param timeval the time of the wait, in seconds
     */
    public void setTimeBetweenUpdate(int timeval)
    {
        if(timeval <= 0) timeval = 1;
        this.timeBetweenUpdate = timeval;
    }

    /**
     * Get the time (in seconds) this <tt>HammerStats</tt> will wait
     * between 2 updates of stats.
     */
    public int getTimeBetweenUpdate()
    {
        return this.timeBetweenUpdate;
    }

    /**
     * Enable or disable the logging of all the stats collected by this
     * <tt>HammerStats</tt>.
     * @param allStats the boolean that enable of disable the logging.
     */
    public void setAllStatsLogging(boolean allStats)
    {
        this.allStatsLogging = allStats;
        if(allStats)
        {
            File saveDir = new File(this.statsDirectoryPath);
            if(saveDir.exists() == false)
            {
                logger.info("Creating " + this.statsDirectoryPath + " directory");
                saveDir.mkdirs();
            }
        }
    }

    /**
     * Enable or disable the logging of the summary stats computed with all
     * the stats collected by this <tt>HammerStats</tt>.
     * @param allStats the boolean that enable of disable the logging.
     */
    public void setSummaryStatsLogging(boolean summaryStats)
    {
        this.summaryStatsLogging = summaryStats;
        if(summaryStats)
        {
            File saveDir = new File(this.statsDirectoryPath);
            if(saveDir.exists() == false)
            {
                saveDir.mkdirs();
            }
        }
    }

    /**
     * Enable or disable the logging of all the stats collected by this
     * <tt>HammerStats</tt>.
     * @param allStats the boolean that enable of disable the logging.
     */
    public void setOverallStatsLogging(boolean overallStats)
    {
        this.overallStatsLogging = overallStats;
        if(overallStats)
        {
            File saveDir = new File(this.statsDirectoryPath);
            if(saveDir.exists() == false)
            {
                logger.info("Creating " + this.statsDirectoryPath + " directory");
                saveDir.mkdirs();
            }
        }
    }


    private class HammerSummaryStats
    {
        AggregateSummaryStatistics aggregateDownloadJitterMs = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateDownloadPercentLoss = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateDownloadRateKiloBitPerSec = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateJitterBufferDelayMs = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateJitterBufferDelayPackets = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbDiscarded = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbDiscardedFull = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbDiscardedLate = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbDiscardedReset = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbDiscardedShrink = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbFec = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbPackets = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbPacketsLost = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbReceivedBytes = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateNbSentBytes = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregatePacketQueueCountPackets = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregatePacketQueueSize = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregatePercentDiscarded = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateRttMs = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateUploadJitterMs = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateUploadPercentLoss = new AggregateSummaryStatistics();
        AggregateSummaryStatistics aggregateUploadRateKiloBitPerSec = new AggregateSummaryStatistics();

        SummaryStatistics downloadJitterMs;
        SummaryStatistics downloadPercentLoss;
        SummaryStatistics downloadRateKiloBitPerSec;
        SummaryStatistics jitterBufferDelayMs;
        SummaryStatistics jitterBufferDelayPackets;
        SummaryStatistics nbDiscarded;
        SummaryStatistics nbDiscardedFull;
        SummaryStatistics nbDiscardedLate;
        SummaryStatistics nbDiscardedReset;
        SummaryStatistics nbDiscardedShrink;
        SummaryStatistics nbFec;
        SummaryStatistics nbPackets;
        SummaryStatistics nbPacketsLost;
        SummaryStatistics nbReceivedBytes;
        SummaryStatistics nbSentBytes;
        SummaryStatistics packetQueueCountPackets;
        SummaryStatistics packetQueueSize;
        SummaryStatistics percentDiscarded;
        SummaryStatistics rttMs;
        SummaryStatistics uploadJitterMs;
        SummaryStatistics uploadPercentLoss;
        SummaryStatistics uploadRateKiloBitPerSec;

        public HammerSummaryStats()
        {
            clear();
        }

        public void add(MediaStreamStats stats)
        {
            downloadJitterMs.addValue(stats.getDownloadJitterMs());
            downloadPercentLoss.addValue(stats.getDownloadPercentLoss());
            downloadRateKiloBitPerSec.addValue(stats.getDownloadRateKiloBitPerSec());
            jitterBufferDelayMs.addValue(stats.getJitterBufferDelayMs());
            jitterBufferDelayPackets.addValue(stats.getJitterBufferDelayPackets());
            nbDiscarded.addValue(stats.getNbDiscarded());
            nbDiscardedFull.addValue(stats.getNbDiscardedFull());
            nbDiscardedLate.addValue(stats.getNbDiscardedLate());
            nbDiscardedReset.addValue(stats.getNbDiscardedReset());
            nbDiscardedShrink.addValue(stats.getNbDiscardedShrink());
            nbFec.addValue(stats.getNbFec());
            nbPackets.addValue(stats.getNbPackets());
            nbPacketsLost.addValue(stats.getNbPacketsLost());
            nbReceivedBytes.addValue(stats.getNbReceivedBytes());
            nbSentBytes.addValue(stats.getNbSentBytes());
            packetQueueCountPackets.addValue(stats.getPacketQueueCountPackets());
            packetQueueSize.addValue(stats.getPacketQueueSize());
            percentDiscarded.addValue(stats.getPercentDiscarded());
            rttMs.addValue(stats.getRttMs());
            uploadJitterMs.addValue(stats.getUploadJitterMs());
            uploadPercentLoss.addValue(stats.getUploadPercentLoss());
            uploadRateKiloBitPerSec.addValue(stats.getUploadRateKiloBitPerSec());

        }

        public void clear()
        {
            downloadJitterMs =
                aggregateDownloadJitterMs.createContributingStatistics();
            downloadPercentLoss =
                aggregateDownloadPercentLoss.createContributingStatistics();
            downloadRateKiloBitPerSec =
                aggregateDownloadRateKiloBitPerSec.createContributingStatistics();
            jitterBufferDelayMs =
                aggregateJitterBufferDelayMs.createContributingStatistics();
            jitterBufferDelayPackets =
                aggregateJitterBufferDelayPackets.createContributingStatistics();
            nbDiscarded =
                aggregateNbDiscarded.createContributingStatistics();
            nbDiscardedFull =
                aggregateNbDiscardedFull.createContributingStatistics();
            nbDiscardedLate =
                aggregateNbDiscardedLate.createContributingStatistics();
            nbDiscardedReset =
                aggregateNbDiscardedReset.createContributingStatistics();
            nbDiscardedShrink =
                aggregateNbDiscardedShrink.createContributingStatistics();
            nbFec =
                aggregateNbFec.createContributingStatistics();
            nbPackets =
                aggregateNbPackets.createContributingStatistics();
            nbPacketsLost =
                aggregateNbPacketsLost.createContributingStatistics();
            nbReceivedBytes =
                aggregateNbReceivedBytes.createContributingStatistics();
            nbSentBytes =
                aggregateNbSentBytes.createContributingStatistics();
            packetQueueCountPackets =
                aggregatePacketQueueCountPackets.createContributingStatistics();
            packetQueueSize =
                aggregatePacketQueueSize.createContributingStatistics();
            percentDiscarded =
                aggregatePercentDiscarded.createContributingStatistics();
            rttMs =
                aggregateRttMs.createContributingStatistics();
            uploadJitterMs =
                aggregateUploadJitterMs.createContributingStatistics();
            uploadPercentLoss =
                aggregateUploadPercentLoss.createContributingStatistics();
            uploadRateKiloBitPerSec =
                aggregateUploadRateKiloBitPerSec.createContributingStatistics();
        }

        public String getMaxJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                downloadJitterMs.getMax(),
                downloadPercentLoss.getMax(),
                downloadRateKiloBitPerSec.getMax(),
                jitterBufferDelayMs.getMax(),
                jitterBufferDelayPackets.getMax(),
                nbDiscarded.getMax(),
                nbDiscardedFull.getMax(),
                nbDiscardedLate.getMax(),
                nbDiscardedReset.getMax(),
                nbDiscardedShrink.getMax(),
                nbFec.getMax(),
                nbPackets.getMax(),
                nbPacketsLost.getMax(),
                nbReceivedBytes.getMax(),
                nbSentBytes.getMax(),
                packetQueueCountPackets.getMax(),
                packetQueueSize.getMax(),
                percentDiscarded.getMax(),
                rttMs.getMax(),
                uploadJitterMs.getMax(),
                uploadPercentLoss.getMax(),
                uploadRateKiloBitPerSec.getMax());
            return str;
        }

        public String getMeanJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                downloadJitterMs.getMean(),
                downloadPercentLoss.getMean(),
                downloadRateKiloBitPerSec.getMean(),
                jitterBufferDelayMs.getMean(),
                jitterBufferDelayPackets.getMean(),
                nbDiscarded.getMean(),
                nbDiscardedFull.getMean(),
                nbDiscardedLate.getMean(),
                nbDiscardedReset.getMean(),
                nbDiscardedShrink.getMean(),
                nbFec.getMean(),
                nbPackets.getMean(),
                nbPacketsLost.getMean(),
                nbReceivedBytes.getMean(),
                nbSentBytes.getMean(),
                packetQueueCountPackets.getMean(),
                packetQueueSize.getMean(),
                percentDiscarded.getMean(),
                rttMs.getMean(),
                uploadJitterMs.getMean(),
                uploadPercentLoss.getMean(),
                uploadRateKiloBitPerSec.getMean());
            return str;
        }

        public String getMinJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                downloadJitterMs.getMin(),
                downloadPercentLoss.getMin(),
                downloadRateKiloBitPerSec.getMin(),
                jitterBufferDelayMs.getMin(),
                jitterBufferDelayPackets.getMin(),
                nbDiscarded.getMin(),
                nbDiscardedFull.getMin(),
                nbDiscardedLate.getMin(),
                nbDiscardedReset.getMin(),
                nbDiscardedShrink.getMin(),
                nbFec.getMin(),
                nbPackets.getMin(),
                nbPacketsLost.getMin(),
                nbReceivedBytes.getMin(),
                nbSentBytes.getMin(),
                packetQueueCountPackets.getMin(),
                packetQueueSize.getMin(),
                percentDiscarded.getMin(),
                rttMs.getMin(),
                uploadJitterMs.getMin(),
                uploadPercentLoss.getMin(),
                uploadRateKiloBitPerSec.getMin());
            return str;
        }

        public String getStandardDeviationJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                downloadJitterMs.getStandardDeviation(),
                downloadPercentLoss.getStandardDeviation(),
                downloadRateKiloBitPerSec.getStandardDeviation(),
                jitterBufferDelayMs.getStandardDeviation(),
                jitterBufferDelayPackets.getStandardDeviation(),
                nbDiscarded.getStandardDeviation(),
                nbDiscardedFull.getStandardDeviation(),
                nbDiscardedLate.getStandardDeviation(),
                nbDiscardedReset.getStandardDeviation(),
                nbDiscardedShrink.getStandardDeviation(),
                nbFec.getStandardDeviation(),
                nbPackets.getStandardDeviation(),
                nbPacketsLost.getStandardDeviation(),
                nbReceivedBytes.getStandardDeviation(),
                nbSentBytes.getStandardDeviation(),
                packetQueueCountPackets.getStandardDeviation(),
                packetQueueSize.getStandardDeviation(),
                percentDiscarded.getStandardDeviation(),
                rttMs.getStandardDeviation(),
                uploadJitterMs.getStandardDeviation(),
                uploadPercentLoss.getStandardDeviation(),
                uploadRateKiloBitPerSec.getStandardDeviation());
            return str;
        }

        public String getSumJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                downloadJitterMs.getSum(),
                downloadPercentLoss.getSum(),
                downloadRateKiloBitPerSec.getSum(),
                jitterBufferDelayMs.getSum(),
                jitterBufferDelayPackets.getSum(),
                nbDiscarded.getSum(),
                nbDiscardedFull.getSum(),
                nbDiscardedLate.getSum(),
                nbDiscardedReset.getSum(),
                nbDiscardedShrink.getSum(),
                nbFec.getSum(),
                nbPackets.getSum(),
                nbPacketsLost.getSum(),
                nbReceivedBytes.getSum(),
                nbSentBytes.getSum(),
                packetQueueCountPackets.getSum(),
                packetQueueSize.getSum(),
                percentDiscarded.getSum(),
                rttMs.getSum(),
                uploadJitterMs.getSum(),
                uploadPercentLoss.getSum(),
                uploadRateKiloBitPerSec.getSum());
            return str;
        }

        public String getVarianceJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                downloadJitterMs.getVariance(),
                downloadPercentLoss.getVariance(),
                downloadRateKiloBitPerSec.getVariance(),
                jitterBufferDelayMs.getVariance(),
                jitterBufferDelayPackets.getVariance(),
                nbDiscarded.getVariance(),
                nbDiscardedFull.getVariance(),
                nbDiscardedLate.getVariance(),
                nbDiscardedReset.getVariance(),
                nbDiscardedShrink.getVariance(),
                nbFec.getVariance(),
                nbPackets.getVariance(),
                nbPacketsLost.getVariance(),
                nbReceivedBytes.getVariance(),
                nbSentBytes.getVariance(),
                packetQueueCountPackets.getVariance(),
                packetQueueSize.getVariance(),
                percentDiscarded.getVariance(),
                rttMs.getVariance(),
                uploadJitterMs.getVariance(),
                uploadPercentLoss.getVariance(),
                uploadRateKiloBitPerSec.getVariance());
            return str;
        }

        public String getAggregateMaxJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                aggregateDownloadJitterMs.getMax(),
                aggregateDownloadPercentLoss.getMax(),
                aggregateDownloadRateKiloBitPerSec.getMax(),
                aggregateJitterBufferDelayMs.getMax(),
                aggregateJitterBufferDelayPackets.getMax(),
                aggregateNbDiscarded.getMax(),
                aggregateNbDiscardedFull.getMax(),
                aggregateNbDiscardedLate.getMax(),
                aggregateNbDiscardedReset.getMax(),
                aggregateNbDiscardedShrink.getMax(),
                aggregateNbFec.getMax(),
                aggregateNbPackets.getMax(),
                aggregateNbPacketsLost.getMax(),
                aggregateNbReceivedBytes.getMax(),
                aggregateNbSentBytes.getMax(),
                aggregatePacketQueueCountPackets.getMax(),
                aggregatePacketQueueSize.getMax(),
                aggregatePercentDiscarded.getMax(),
                aggregateRttMs.getMax(),
                aggregateUploadJitterMs.getMax(),
                aggregateUploadPercentLoss.getMax(),
                aggregateUploadRateKiloBitPerSec.getMax());
            return str;
        }

        public String getAggregateMeanJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                aggregateDownloadJitterMs.getMean(),
                aggregateDownloadPercentLoss.getMean(),
                aggregateDownloadRateKiloBitPerSec.getMean(),
                aggregateJitterBufferDelayMs.getMean(),
                aggregateJitterBufferDelayPackets.getMean(),
                aggregateNbDiscarded.getMean(),
                aggregateNbDiscardedFull.getMean(),
                aggregateNbDiscardedLate.getMean(),
                aggregateNbDiscardedReset.getMean(),
                aggregateNbDiscardedShrink.getMean(),
                aggregateNbFec.getMean(),
                aggregateNbPackets.getMean(),
                aggregateNbPacketsLost.getMean(),
                aggregateNbReceivedBytes.getMean(),
                aggregateNbSentBytes.getMean(),
                aggregatePacketQueueCountPackets.getMean(),
                aggregatePacketQueueSize.getMean(),
                aggregatePercentDiscarded.getMean(),
                aggregateRttMs.getMean(),
                aggregateUploadJitterMs.getMean(),
                aggregateUploadPercentLoss.getMean(),
                aggregateUploadRateKiloBitPerSec.getMean());
            return str;
        }

        public String getAggregateMinJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                aggregateDownloadJitterMs.getMin(),
                aggregateDownloadPercentLoss.getMin(),
                aggregateDownloadRateKiloBitPerSec.getMin(),
                aggregateJitterBufferDelayMs.getMin(),
                aggregateJitterBufferDelayPackets.getMin(),
                aggregateNbDiscarded.getMin(),
                aggregateNbDiscardedFull.getMin(),
                aggregateNbDiscardedLate.getMin(),
                aggregateNbDiscardedReset.getMin(),
                aggregateNbDiscardedShrink.getMin(),
                aggregateNbFec.getMin(),
                aggregateNbPackets.getMin(),
                aggregateNbPacketsLost.getMin(),
                aggregateNbReceivedBytes.getMin(),
                aggregateNbSentBytes.getMin(),
                aggregatePacketQueueCountPackets.getMin(),
                aggregatePacketQueueSize.getMin(),
                aggregatePercentDiscarded.getMin(),
                aggregateRttMs.getMin(),
                aggregateUploadJitterMs.getMin(),
                aggregateUploadPercentLoss.getMin(),
                aggregateUploadRateKiloBitPerSec.getMin());
            return str;
        }

        public String getAggregateStandardDeviationJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                aggregateDownloadJitterMs.getStandardDeviation(),
                aggregateDownloadPercentLoss.getStandardDeviation(),
                aggregateDownloadRateKiloBitPerSec.getStandardDeviation(),
                aggregateJitterBufferDelayMs.getStandardDeviation(),
                aggregateJitterBufferDelayPackets.getStandardDeviation(),
                aggregateNbDiscarded.getStandardDeviation(),
                aggregateNbDiscardedFull.getStandardDeviation(),
                aggregateNbDiscardedLate.getStandardDeviation(),
                aggregateNbDiscardedReset.getStandardDeviation(),
                aggregateNbDiscardedShrink.getStandardDeviation(),
                aggregateNbFec.getStandardDeviation(),
                aggregateNbPackets.getStandardDeviation(),
                aggregateNbPacketsLost.getStandardDeviation(),
                aggregateNbReceivedBytes.getStandardDeviation(),
                aggregateNbSentBytes.getStandardDeviation(),
                aggregatePacketQueueCountPackets.getStandardDeviation(),
                aggregatePacketQueueSize.getStandardDeviation(),
                aggregatePercentDiscarded.getStandardDeviation(),
                aggregateRttMs.getStandardDeviation(),
                aggregateUploadJitterMs.getStandardDeviation(),
                aggregateUploadPercentLoss.getStandardDeviation(),
                aggregateUploadRateKiloBitPerSec.getStandardDeviation());
            return str;
        }

        public String getAggregateSumJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                aggregateDownloadJitterMs.getSum(),
                aggregateDownloadPercentLoss.getSum(),
                aggregateDownloadRateKiloBitPerSec.getSum(),
                aggregateJitterBufferDelayMs.getSum(),
                aggregateJitterBufferDelayPackets.getSum(),
                aggregateNbDiscarded.getSum(),
                aggregateNbDiscardedFull.getSum(),
                aggregateNbDiscardedLate.getSum(),
                aggregateNbDiscardedReset.getSum(),
                aggregateNbDiscardedShrink.getSum(),
                aggregateNbFec.getSum(),
                aggregateNbPackets.getSum(),
                aggregateNbPacketsLost.getSum(),
                aggregateNbReceivedBytes.getSum(),
                aggregateNbSentBytes.getSum(),
                aggregatePacketQueueCountPackets.getSum(),
                aggregatePacketQueueSize.getSum(),
                aggregatePercentDiscarded.getSum(),
                aggregateRttMs.getSum(),
                aggregateUploadJitterMs.getSum(),
                aggregateUploadPercentLoss.getSum(),
                aggregateUploadRateKiloBitPerSec.getSum());
            return str;
        }

        public String getAggregateVarianceJSON()
        {
            String str = String.format(FakeUserStats.jsonMediaStreamStatsTemplate,
                -1, //ssrc not needed here
                aggregateDownloadJitterMs.getVariance(),
                aggregateDownloadPercentLoss.getVariance(),
                aggregateDownloadRateKiloBitPerSec.getVariance(),
                aggregateJitterBufferDelayMs.getVariance(),
                aggregateJitterBufferDelayPackets.getVariance(),
                aggregateNbDiscarded.getVariance(),
                aggregateNbDiscardedFull.getVariance(),
                aggregateNbDiscardedLate.getVariance(),
                aggregateNbDiscardedReset.getVariance(),
                aggregateNbDiscardedShrink.getVariance(),
                aggregateNbFec.getVariance(),
                aggregateNbPackets.getVariance(),
                aggregateNbPacketsLost.getVariance(),
                aggregateNbReceivedBytes.getVariance(),
                aggregateNbSentBytes.getVariance(),
                aggregatePacketQueueCountPackets.getVariance(),
                aggregatePacketQueueSize.getVariance(),
                aggregatePercentDiscarded.getVariance(),
                aggregateRttMs.getVariance(),
                aggregateUploadJitterMs.getVariance(),
                aggregateUploadPercentLoss.getVariance(),
                aggregateUploadRateKiloBitPerSec.getVariance());
            return str;
        }
    }
}
