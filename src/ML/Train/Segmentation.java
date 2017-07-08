/*
 * So we have here an approach which is very progressive, yet delivers results in a short time. It is also quite insensitive to sound events that could derail the heart beat counting, as the first step provide some good indications of the area where is the real heart beat, one spike makes the closer S1 perhaps not detected, but we gain a good idea of the heart duration.
5) In addition to S1, many other events are detected. A priori we assume that these are the other events S2, S3, S4. Even numbers going higher than four, it is useful for unusual heart sound classification.
6) These two Sx events detections are brought closer together
6-1) The list of S1 events is made more reliable, which makes it possible to deduce the events S2, S3, S4 (Segmentation.java).
6-2) A signature of the heart beat is computed to acknowledging there are more in one heart beat than its time of arrival and duration. We tested several schemes, and decided to use a Huffman compression of the heart beat. We had also the idea to use this to yet another kind of feature detection without training but it is not implemented at the moment because of lack of resources.
7) From there one can either train a HMM, or classify. We go out of cardiac specific, it’s just an HMM
8) One interprets the classification made by the HMM, with a score of similarity and comments on the events Sx
 */
package ML.Train;

import ML.Classify.Observation;
import ML.featureDetection.Event;
import ML.featureDetection.FindBeats;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author jplr
 */
public class Segmentation {

    public ArrayList segmentedBeats = new ArrayList();
    private final FindBeats cb;

    public Segmentation(FindBeats cibi) {
        cb = cibi;
    }

    /**
     * we have ProbableBeats which contains the probable S1 events and moreBeats
     * which contains the probable S1, S2, S3, S4 We will use ProbableBeats to
     * find the probable locations of other Sx events in moreBeats So we use
     * moreBeats to detect events at proximity of the found time for S1 events.
     * Once a S1 S1 is detected, the nextPB S1 before the after this S1 and
     * before the nextPB is supposed to be S2, and so on for S3 and S4.
     *
     * @param norm
     * @param rate
     * @return
     */
    public void segmentation(FindBeats norm, int rate) {
        /*        int S1 = 0;
        int S2 = 0;
        int S3 = 0;
        int S4 = 0; 
        Integer S1MB, S1PB; */

        Iterator itrMB = norm.getMoreBeats().iterator();
        do {
            if (!itrMB.hasNext()) {
                break;
            }

            ArrayList beats = norm.getProbableBeats();
            if (beats.isEmpty()) {
                System.out.println("segmentation: Beat is zero");
                return;
            }

            // This possible S1 event is situated between two S1 events in the 
            // ProbableBeats List,
            // We try to find which S1 is the closest.
            segmentTheBeat(itrMB, norm);

        } while (true);
        return;
    }

    /**
     * This possible S1 event is situated between two S1 events in the
     * ProbableBeats List, We try to find which S1 is the closest.
     *
     * @param S1mb
     * @param norm
     */
    private void segmentTheBeat(Iterator S1mb, FindBeats norm) {
        ArrayList mb = norm.getMoreBeats();
        ArrayList pb = norm.getProbableBeats();
        Event prevPB = null, nextPB = null;
        int cnt = 1;
        Event S1MB = null;
        Observation eventHMM = null;
        int averageDistance = -1;

        ArrayList beats = norm.getProbableBeats();
        
        /* Take in account the "noisyness" of the file */
        int beatsSize = beats.size() - norm.getNoisyFile();
        System.out.println("Segmentation, corrected beat rate: " + beatsSize) ;
        if (beatsSize > 0) {
            averageDistance = norm.getNormalizedData().length / beatsSize;
        } else {
            return;
        }

        Iterator iterPB = pb.iterator();
        // Get the first element of ProbableBeats, that arrives sooner than S1MB
        if (iterPB.hasNext()) {
            prevPB = (Event) iterPB.next();
        }
        boolean flag = true;
        while (iterPB.hasNext()) {
            if (flag == true) {
                nextPB = (Event) iterPB.next();
            } else {
                // flag was found to be false, now make it true
                flag = true;
            }
            // Analyse one beat to discern how many events there are inside
            // one event at a time
            while (S1mb.hasNext()) {
                S1MB = (Event) S1mb.next();
                if (S1MB.timeStampValue().intValue() > nextPB.timeStampValue().intValue()) {
                    prevPB = nextPB;
                    flag = true;
                    cnt = 1;
                    // Nothing found in this PB event, go to the next
                    break;
                }
                // S1 appears in the first quarter of the heartbeat
                // S2 in the second quarter, etc..
                // Event suffix progresses

                float un = averageDistance / (nextPB.timeStampValue().intValue() - prevPB.timeStampValue().intValue());
//                cnt = 1;
                do {
                    eventHMM = analalyse(prevPB.timeStampValue(), nextPB.timeStampValue(), S1MB.timeStampValue(), norm, cnt);
                    if (eventHMM != null) {
                        if(cnt < 5) {
                        addEvents(eventHMM);
                        cnt++;
                        }
                        else {
                            // Mark last event as noisy
                            int inddx = segmentedBeats.size() - 1;
                            Observation lastS4Event = (Observation) segmentedBeats.get(inddx);
                            lastS4Event.addManyEvents(1); ;
                        }
                    } else {
                        // Nothing found in this PB event, go to the next
                        ;
                    }
                    un = un - 1;
                } while (un > 1.5);
                /*
                // S1MB = 1260, prevPB = 1280, nextPB = 3342
                 */
            }
        }
        return;
    }

    public void addEvents(Observation eventHMM) {
        segmentedBeats.add(eventHMM);
    }

    ArrayList getSegmentedBeats() {
        return segmentedBeats;
    }

    private Observation makeHMMObs(String pref, String sufx, int Sx, int S1base, int S1next, FindBeats norm) {
        // make a string for the relative position of the event in the beat
        // Obtain a FFT of the time between S1base and Sx and make a string of it
        // First get the sample between S1base and Sx
        float[] data = norm.getNormalizedData();
        float[] sample = new float[(Sx - S1base) + 1];
        if (sample.length < 3) {
            // not enough values in sample
            return null;
        }

        // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
        System.arraycopy(data, S1base, sample, 0, Sx - S1base);

        float offsetAbs = (Sx - S1base);
        float offsetRel = (float) (Sx - S1base) / (float) (S1next - S1base);

        // calculate a beat signature
        ArrayList efft = cb.beatSign(sample);

        // For the HMM to separate the observations in more cases than S1-S4, we need to
        // add a "minor" numbering to the "Sx" string.
        // However it will be added later, to have a reasonnable amount of Observations
        Observation obs = new Observation(
                pref, sufx, Sx, offsetRel, offsetAbs, efft, 
                norm.getNoisyFile(), norm.getShift(), 0);

        return obs;
    }

    private Observation analalyse(
            Integer prev,
            Integer next,
            Integer S1MB,
            FindBeats norm,
            int cnt
    ) {
        String eventName;

        if ((S1MB.intValue() > prev.intValue()) && (S1MB.intValue() < next.intValue())) {
            eventName = "S" + String.valueOf(cnt);
            Observation eventHMM = makeHMMObs(eventName, "", S1MB.intValue(), prev.intValue(), next.intValue(), norm);
            if (eventHMM == null) {
                return null; // continue
            } else {
                return eventHMM; // 
            }
        }
        return null; // not continue
    }
}
