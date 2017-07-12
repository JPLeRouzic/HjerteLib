/*
     * This calculates the beat in dataIn, this is not the same as heart beat rate
     * because the heart has several sounds inside one beat We will count
     * downward changes that occur at intensities between max value and average
     * value in order to stay away from noise A good reference is a0004.wav that
     * has 36 events and lasts 36 seconds.
     *
 * First, the algorithm applies a low-pass filter. 
 * Filtered audio data are divided into windows of samples.
 * A peak detection algorithm is applied to each window and identifies as peaks 
 * all values above a certain threshold. 
 *
 * For each peak the distance in number of samples between it and its neighbouring peaks is stored.
 * For each distance between peaks the algorithm counts how many times it has been detected. 
 * Once all windows are processed the algorithm has built a map distanceHistogram where for each 
 * possible distance its number of occurrences is stored.
 */
package ML.featureDetection;

import java.util.ArrayList;
import java.util.BitSet;

/**
 *
 * @author jplr
 */
public class FindBeats {

    // bag of events probably including S1, S2, S3, S4 sounds
    private ArrayList moreBeatEvents;

    // S1 events as found in data flow, maybe wrong because of spikes
    ArrayList probableS1Beats;

    // Possibly S1 events
    private ArrayList candidateBeats;

    // Normalized data that is correlated to moreBeatEvents and probableS1Beats
    private float[] normalizedData;

    // average value of sound 
    float aver;

    // max value of sound
    float maxi;

    // treshhold to obtain the beat rate, useful to qualify the quality of the file sound
    // and helping in segmenting the heart sounds
    float treshFind;

    // Used to do a rough evaluation of beat rate and treshold, before aiming at a more precise evaluation
    int RBF_Beats = 0;
    float RBF_treshhold = 0;

    // Evaluate is file is noisy
    int noisyFile = 0;
    private int eventShift;
    private boolean randm = false ;


    /**
     */
    public FindBeats() {
        treshFind = 0;

        moreBeatEvents = new ArrayList();
        probableS1Beats = new ArrayList();
        candidateBeats = new ArrayList();
    }

    /**
     * Find average in data
     *
     * @param data
     */
    private void averMax(float[] data) {
        float absData;
        int idx = 0;
        float sum = 0;

        // find this file average
        while (idx < data.length) {
            absData = data[idx];
            if (absData < 0) {
                absData = -absData;
            }
            // in order to find average value
            sum += absData;
            // in order to find maximum value
            if (absData > maxi) {
                maxi = absData;
            }

            idx++;
        }

        // find this file average
        aver = sum / data.length;
    }

    /**
     * Loop varying the guessed heart rate, till some significant value rises:
     * At least more Sx events than there are S1 events
     *
     * @param data_norm
     * @param smplingRate
     * @param heart_rate
     */
    public void calcBeat(float[] data_norm, int smplingRate, int heart_rate) {
    int beats = heart_rate;
        int lastBeats;
        int nbEssai = 1;
        boolean rndm = false ;
        do {
            lastBeats = moreBeatEvents.size();
            calcBeatTreshold(data_norm, smplingRate, beats);
            // the beats found are at random, which is unlikely
            // calcBeatTreshold restored the previous value, which is likely the best we ould found
            if(randm == true) {
                break ;
            }
            beats = (int) (beats * 1.5);
            nbEssai++;
        } while ((moreBeatEvents.size() > lastBeats) && (nbEssai < 4) && (probableS1Beats.size() > (heart_rate * 0.7)));
    }

    /**
     * Try to identity S1 sound in one beat, then find more heart sounds
     *
     * @param data
     * @param sampling_rate
     * @param beatSec
     *
     */
    public void calcBeatTreshold(float[] data, int sampling_rate, int beatSec) {
        float[] dataIn = data;
        float max, ave;
        float s2Shift = (float) 0.15;
        float earlyS1, lateS1;

        // average beat rate may be one per second.
        float nbSecInFile = dataIn.length / sampling_rate;
        if (nbSecInFile == 0) {
            System.out.println("nbSecInFile is zero");
        }

        averMax(dataIn);
        ave = aver;
        max = maxi;

        // evaluating the tresholdS1        
        treshFind = 6;

        earlyS1 = sampling_rate * (float) 0.5;
        lateS1 = sampling_rate * (float) 1.5;

        // Rough evaluation of treshold, if beat rate not provided in advance
        if (beatSec == 0) {
            roughTreshFind(dataIn, sampling_rate, earlyS1, lateS1, s2Shift, ave, max, nbSecInFile);
            beatSec = RBF_Beats;
            treshFind = RBF_treshhold;
        }

        /* It uses the global value of treshFind */
        probableS1Beats = calcBeat2(
                dataIn, sampling_rate, 
                beatSec, earlyS1, lateS1, s2Shift, 
                ave, max, 
                nbSecInFile);

        // Save data that was normalized in inner loops
        normalizedData = dataIn;

        // Find next sounds in this beat
        float tresholdS1 = (ave + (2 * max)) / (2 + 1);
        if (probableS1Beats.size() > 0) {
            moreBeatEvents = findNextSounds(dataIn, tresholdS1, probableS1Beats.size() * 4);
        }
    }

    /**
     * loop varying treshhold, till some significant value rises at least more
     * Sx events than there are S1 events It uses the global value of treshFind
     *
     * @param data
     * @param sampling_rate
     * @param beatSec
     */
    private ArrayList calcBeat2(float[] data, int sampling_rate, int beatSec,
            float earlyS1, float lateS1, float s2Shift,
            float ave, float max, float nbSecInFile) {
        ArrayList events;
        ArrayList lastEvents = null;
        int lastEventsSize = 0;
        do {
            events = calcRateRuleOfThumb(
                    data, sampling_rate, beatSec, earlyS1, lateS1, s2Shift, ave,
                    max, nbSecInFile, treshFind);
            if (events == null) {
                System.out.println("null in calcBeat2");
            }

            // Leave the loop if less events are detected than previously
            if (events.size() < lastEventsSize) {
                return events;
            }

            /* If events have some randomness, it means noise is detected
             */
            detectRandomness(events);
            if ((randm == true) && (events.size() > (beatSec * 0.666))) {
                return lastEvents;
            }

            // store it for later reference
            lastEvents = (ArrayList) events.clone();

            // Finer grain as tresholdS1 becomes only slightly higher than average
            if (treshFind > 1) {
                treshFind -= 0.5;
            } else {
                treshFind -= 0.125;
            }
        } while (treshFind > 0.05);

        // If we breat out the loop, we must return the events list
        return events;
    }

    private void detectRandomness(ArrayList events) {
        int tot = 0;
        float lastMin = 999999999;
        float min = 0;
        float lastMax = 0;
        float max = 0;
        int timeStamp = -1;

        if (events.size() == 0) {
            randm = false;
            return ;
        }
        for (int idx = 0; idx < events.size(); idx++) {
            timeStamp = ((Event) events.get(idx)).timeStampValue().intValue();
            min = timeStamp;
            if (min < lastMin) {
                lastMin = min;
            }
            max = timeStamp;
            if (max > lastMax) {
                lastMax = max;
            }

            tot += timeStamp;
        }
        int ave = tot / events.size();
        float tresh = (lastMax - lastMin) / (ave);

        // hardwired constant
        if ((tresh) > 2.2) {
            randm = true;
        } else {
            randm = false;
        }
    }

    /**
     * This selects a rate, based on rule of thumbs. The rate was calculated
     * with a given treshold value and a previous rough estimation of the beat
     * rate
     *
     * @param dataIn
     * @param sampling_rate
     * @param beatSec
     * @param earlyS1
     * @param lateS1
     * @param s2Shift
     * @param ave
     * @param max
     * @param nbSecInFile
     * @param treshFnd
     * @return
     */
    private ArrayList calcRateRuleOfThumb(float[] dataIn, int sampling_rate, int beatSec,
            float earlyS1, float lateS1, float s2Shift,
            float ave, float max, float nbSecInFile,
            float treshFnd
    ) {

        ArrayList events = calcBeatFind(dataIn, sampling_rate, earlyS1, lateS1, s2Shift, treshFnd);

        // Number of events per minute
        int nbEvents = (int) ((events.size() * 60) / nbSecInFile);

        int floor_low, floor_high, ceiling_low, ceiling_high;
        floor_low = (int) (beatSec * 0.41675);       // 25 events per minute 
        floor_high = (int) (beatSec * 0.83335);      // 50 events per minute 
        ceiling_low = (int) (beatSec * 1.33335);     // 80 events per minute 
        ceiling_high = (int) (beatSec * 2.6667);   // 160 events per minute 

        if ((nbEvents > (floor_low)) && (nbEvents < (ceiling_high))) {
            events = isRateAcceptable(nbEvents, floor_high, ceiling_low, dataIn,
                    ave, max, treshFnd, events);
            // troisième cas ?
        } else if (events.size() > (nbSecInFile * 4)) {
            // Something wrong with dataIn, way too much events, so we filter it heavily
            Resample resp = new Resample();
            dataIn = resp.downSample(dataIn, sampling_rate, 1024);
        } else if (events.size() < (nbSecInFile / 3)) {
            // Something wrong with dataIn, not enough events, so we normalize dataIn
            NormalizeBeat nb = new NormalizeBeat();
            dataIn = nb.normalizeAmplitude(dataIn);
        }
        return events;
    }

    /**
     * Examine if a heart rate is within limits we find acceptable
     *
     * @param nbEvents
     * @param floor_high
     * @param ceiling_low
     * @param dataIn
     * @param ave
     * @param max
     * @param treshFnd
     * @param events
     * @return
     */
    private ArrayList isRateAcceptable(
            int nbEvents, int floor_high, int ceiling_low, float[] dataIn,
            float ave, float max, float treshFnd, ArrayList events
    ) {
        ArrayList preBeats = new ArrayList();
        ArrayList prepreBeats = new ArrayList();

        if ((nbEvents > (floor_high)) && (nbEvents < (ceiling_low))) {
            normalizedData = dataIn;
            probableS1Beats = events;
            float tresholdS1 = (ave + (treshFnd * max)) / (treshFnd + 1);
            moreBeatEvents = findNextBeatEvents(dataIn, tresholdS1);
            noisyFile++;
//            System.out.println("isRateAcceptable 0");
            return probableS1Beats;
        }
        // Normally we should converge, if not then return last heart rate
        int un = preBeats.size() - prepreBeats.size();
        int deux = events.size() - preBeats.size();
        if ((un < deux) && (un > 0) && (deux > 0) && (preBeats.size() > 0)) {
            /*        
            * We probably rarely go through this branch
             */
            normalizedData = dataIn;
            probableS1Beats = events;
            float tresholdS1 = (ave + (treshFnd * max)) / (treshFnd + 1);
            if (probableS1Beats.size() > 0) {
                moreBeatEvents = findNextBeatEvents(dataIn, tresholdS1);
//            System.out.println("isRateAcceptable 1");
                return moreBeatEvents;
            }
        } else {
            // Maybe the real heart wrong is wrong
            if ((events.size() == preBeats.size()) && (events.size() == prepreBeats.size())) {
                candidateBeats = events;
                // C'est un break ?????????????
                // Something wrong in isRateAcceptable
//            System.out.println("isRateAcceptable 2");
                return null;
            }
            prepreBeats = preBeats;
            preBeats = events;
        }
        candidateBeats = events;
//            System.out.println("isRateAcceptable 3");
        noisyFile++;
        return candidateBeats;
    }

    /**
     * update moreBeatEvents
     */
    private ArrayList findNextBeatEvents(
            float[] dataIn,
            float treshFnd) {

        float trshFnd = treshFnd;
        /*        
        // Find next sounds in this beat, we try to find four times the probableS1Beats
        // for having candidates for S1, S2, S3, S4 events
//            System.out.println("trshFnd= " + trshFnd) ;
        }
         */

        if (probableS1Beats.size() > 0) {
            moreBeatEvents = findNextSounds(dataIn, trshFnd, probableS1Beats.size() * 4);
        }
        return moreBeatEvents;
    }

    /**
     * This is for finding S1 events, it is independant of sampling rate and
     * contains no prior knowledge about beat rate It applies a filter at 40Hz
     * (upper beat frequency = 4Hz, max four events per beat, at least two
     * sampling per event and filter out any 50/60Hz from power lines so 40Hz is
     * fine
     *
     * Time Event 1 S1 2 3 Ec 4 5 6 7 8 S2 9 10 OS 11 12 13 S3 14 15 16 17 18 19
     * S4 20
     *
     * @param dataIn
     * @param sampling_rate
     * @param treshFnd
     * @param ave
     * @param max
     *
     * @return
     */
    ArrayList calcBeatFind(
            float[] dataIn,
            int smplRate,
            float earlyS1, float lateS1,
            float s2Shift,
            float treshFnd
    ) {
        float ave, max;
        int idxGlobal;
        int early = 0;
        int late = 0;
        ArrayList BeatS1Rate = new ArrayList();
        boolean S1Flag = false;
        float tresholdS1;
        float diff = 0;
        float lastAverage;

        // Calculate average and maximum positive value in the sound sample
        // Results in fields aver and maxi
        averMax(dataIn);
        ave = aver;
        max = maxi;

        if (max > 1) {
            max = (float) 0.99;
        }

        if (ave <= 0) {
            ave = max / 3;
        }

        /**/
        tresholdS1 = (ave + (treshFnd * max)) / (treshFnd + 1);

        idxGlobal = 1;
        while (idxGlobal < dataIn.length) {
            // It applies a filter at 40Hz (see above)
            int winLength = smplRate / 40;
            lastAverage = lastWinAve(dataIn, idxGlobal, winLength);
            if ((dataIn[idxGlobal] > tresholdS1) && (S1Flag == true)) {
                if (lastAverage < tresholdS1) {
                    // We went through the tresholdS1 upward, while counting was forbidden
                    // We need to reautorize it at the next downward tresholdS1
                    S1Flag = false;
                }
            }
            if ((dataIn[idxGlobal] < tresholdS1) && (S1Flag == false) && (lastAverage > tresholdS1)) {
                // So we are in a situation when we went through the tresholdS1 when we went 
                // from [idxGlobal-1] to [idxGlobal]
                // Let's count a beat, and rise a flag to count only once downward
                int un = BeatS1Rate.size();
                if (un > 0) {
                    diff = idxGlobal - ((Event) BeatS1Rate.get(un - 1)).timeStampValue().intValue();
                } else {
                    diff = earlyS1 + 1;
                }
                // Is it possibly too early?
                if (diff < earlyS1) {
                    early++;
                    idxGlobal++;
                    continue;
                }
                // Is it possibly too late?
                if (diff > lateS1) {
                    early--;
                    tresholdS1 = (float) ((double) tresholdS1 * 0.85D);
                    if (un > 1) {
                        idxGlobal = ((Event) BeatS1Rate.get(un - 2)).timeStampValue().intValue();
                        continue;
                    }
                }
                Event event = new Event(Integer.valueOf(idxGlobal), tresholdS1, early, late);
                BeatS1Rate.add(event);
                S1Flag = true;

                // try to move idxGlobal a bit, in a effort to thwart spikes
                idxGlobal += s2Shift;
            }
            idxGlobal++;
        }
        setShift(early);
        return BeatS1Rate;
    }

    public ArrayList getMoreBeats() {
        return moreBeatEvents;
    }

    public ArrayList getProbableBeats() {
        return probableS1Beats;
    }

    public ArrayList getCandidateBeats() {
        return candidateBeats;
    }

    public float[] getNormalizedData() {
        return normalizedData;
    }

    /**
     * Average the past "windowLength" slots in "dataIn" before "idxGlbal" This
     * is because the number must be an average of past data
     *
     */
    private float lastWinAve(float[] dataIn, int idxGlbal, int windowLength) {
        int cnt = 0, idxWindow;
        int idxGlobal = idxGlbal;
        float prevWindowAve = 0;

        if (idxGlobal < windowLength) {
            idxGlobal = windowLength;
        }

        cnt = 0;
        prevWindowAve = 0;
        idxWindow = idxGlobal - windowLength;
        while (idxWindow < idxGlobal) {
            if (idxWindow < dataIn.length) {
                if (dataIn[idxWindow] > 0) {
                    prevWindowAve += dataIn[idxWindow];
                    cnt++;
                }
                idxWindow++;
            } else {
                //             int y = 0 ;
                //            break ;
            }
        }
        if (cnt > 0) {
            return prevWindowAve / cnt;
        } else {
            return dataIn[dataIn.length - 1];
        }
    }

    /**
     * The purpose of this method is to calculate a "signature" of the beat. It
     * is a bit string, long as a beat lasts, and having only "0" or "1" values
     * It is created by saturing the signal and compressing it with RLL.
     *
     * It is supposed to be faster than FFT
     *
     */
    public ArrayList beatSign(float[] data) {
        // the signature is found
        BitSet binary = new BitSet();

        int jdx = 0;
        float ave = 0, max = 0;
        float treshold = 0;
        // find this file average
        averMax(data);
        ave = aver;
        max = maxi;

        treshold = ((2 * ave) + max) / 3;

        binary = betBitSlice(data, treshold);

        // Now do the Run Limited Length algorithm
        int j;
        ArrayList reslt = new ArrayList();

        // We start always with a count of "1" at index == "0"
        int k = 0;
        boolean a = binary.get(0);
        while ((k < binary.size()) && (a != true)) {
            a = binary.get(k);
            k++;
        }

        for (int i = 0; i < binary.length(); i++) {
            int runLength = 1;
            while (i + 1 < binary.length() && binary.get(i) == binary.get(i + 1)) {
                runLength++;
                i++;
            }

            reslt.add(new Integer(runLength));
        }
        return reslt;
    }

    private BitSet betBitSlice(float[] data, float treshold) {
        // treshold is a value between the average value and the max value
        BitSet binary = new BitSet();
        // 
        int idx = 0;
        // find this file average
        while (idx < data.length) {
            if (data[idx] > treshold) {
                binary.set(idx);
            } else {
                binary.clear(idx);
            }
            idx++;
        }
        return binary;
    }

    private ArrayList findNextSounds(float[] data, float treshold, int nbBeats) {
        // treshold is a value between the average value and the max value
        ArrayList binary = new ArrayList();
        float ave;
        int winWidth = (data.length / (nbBeats * 32));
        int idx = 0;

        // find this file average
        while (binary.size() < nbBeats) {
            binary.clear();
            idx = 0;
            while (idx < data.length) {
                ave = lastWinAve(data, idx, winWidth);
                if (ave > treshold) {
                    binary.add(new Event(Integer.valueOf(idx), treshold, 0, 0));
//                    System.out.println("findNextSounds, idx= " + idx + ",    treshold= " + treshold);
                }
                idx += 30;
            }
            treshold = (float) (treshold * 0.8);
            if (treshold < 0.05) {
                break;
            }
        }
        return binary;
    }

    /**
     * This aims at finding a reasonable treshold
     *
     * @param dataIn
     * @param earlyS1
     * @param lateS1
     * @param s2Shift
     * @param f
     * @return
     */
    void roughTreshFind(
            float[] dataIn,
            int smplRate,
            float earlyS1, float lateS1,
            float s2Shift,
            float average,
            float max,
            float nbSecInFile
    ) {
        int beats;
        RBF_Beats = 0;
        RBF_treshhold = max;
        ArrayList events;

        do {
            RBF_treshhold = (float) (RBF_treshhold * 0.8);
            events = calcBeatRough(dataIn, smplRate, earlyS1, lateS1, s2Shift, RBF_treshhold, average, max);
            beats = events.size();
            RBF_Beats = (int) ((beats * 60) / nbSecInFile);
        } while (((RBF_Beats < 50) || (RBF_Beats > 150)) && (RBF_treshhold > average));
    }

    /**
     * We apply a very low pass filter, in order to simplify our task/ However
     * as a beat rate can go from 0.5Hz to 4Hz, the filter is at 10Hz/ So the
     * size of the window is:"sample rate / 1"
     *
     * @param dataIn
     * @param earlyS1
     * @param lateS1
     * @param s2Shift
     * @param treshold
     * @param ave
     * @param max
     * @return
     */
    ArrayList calcBeatRough(
            float[] dataIn,
            int smplRate,
            float earlyS1, float lateS1,
            float s2Shift,
            float treshold,
            float ave,
            float max
    ) {
        ;
        int idxGlobal;
        ArrayList BeatS1Rate = new ArrayList();
        float lastAverage;

        idxGlobal = 1;
        while (idxGlobal < dataIn.length) {
            // the filter is at 10Hz
            lastAverage = lastWinAve(dataIn, idxGlobal, smplRate / 10);

            if ((dataIn[idxGlobal] < treshold) && (lastAverage < treshold)) {
                // Both dataIn and lastAverage are below treshold
                // So no notable event

            }

            if ((dataIn[idxGlobal] < treshold) && (lastAverage > treshold)) {
                // We are going down

                BeatS1Rate.add(Integer.valueOf(idxGlobal));

                // try to move idxGlobal a bit, in a effort to thwart spikes
                idxGlobal += s2Shift;
            }

            if ((dataIn[idxGlobal] > treshold) && (lastAverage < treshold)) {
                // We are going up
            }

            if ((dataIn[idxGlobal] > treshold) && (lastAverage > treshold)) {
                // Both dataIn and lastAverage are above treshold
                // So no notable event

            }

            idxGlobal++;
        }
        return BeatS1Rate;
    }

    /**
     * A Heuristic to mitigate noise
     *
     * @return
     */
    public int getNoisyFile() {
        float div = 4 * (moreBeatEvents.size() / probableS1Beats.size());
        return (int) ((4 * noisyFile) / div);
    }

    public int getShift() {
        return eventShift;
    }

    public void setShift(int shift) {
        eventShift = shift;
    }

    public float getTreshHold() {
        return maxi / aver; // FIXME is 0
    }

}
