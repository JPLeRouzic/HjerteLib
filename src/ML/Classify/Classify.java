package ML.Classify;

import ML.Train.HMM;
import ML.Train.Segmentation;
import ML.featureDetection.FindBeats;
import ML.featureDetection.NormalizeBeat;
import Misc.Main.EntryPoint;
import Misc.sampled.AudioSamples;
import java.util.ArrayList;

public class Classify {

    Segmentation segmt;
    float similarity;
    EntryPoint goglEP = new EntryPoint() ;

    public Classify(AudioSamples audio_data) {
        ArrayList beats = null;

        float samples[] = audio_data.getSamplesMixedDown();
        int smplingRate = (int) audio_data.getSamplingRate();

        int heart_rate = 60;

        /**
         * Normalize dynamics of signal
         *
         */
        NormalizeBeat norm = new NormalizeBeat();

        float[] data_norm = norm.normalizeAmplitude(samples);

        FindBeats cb = new FindBeats();

        // calculate beat rate
        cb.calcBeat1(data_norm, smplingRate, heart_rate);

        // calculate beat rate
        beats = cb.getProbableBeats();

        /**
         *
         * Now that we have a clean sound, we will segment it to recognize the
         * significant times like the locations for S1, systole, s2 and diastole
         *
         * This is the labelled observations private ArrayList<String>
         * moreBeatsType = new ArrayList<String>(); private ArrayList<Integer>
         * moreBeatsIndx = new ArrayList<Integer>();
         *
         * Those times (S1, sys, S2, dia) will be our HMM states, and we must
         * have an observation matrix as input to the training, The result of
         * training should fill in the state transition matrix.
         */
        segmt = new Segmentation(cb);

        segmt.segmentation(cb, smplingRate);

        // Add suffix to Observations names
        // For the HMM to separate the observations in more cases than S1-S4, we need to
        // add a "minor" numbering to the "Sx" string.
        // However it is added later than primary name, to have a reasonable amount of Observations
        // (not having hundreds that are destination, only once)
        // This because otherwise having only four kind of "Observation" is not very helpful
        ArrayList obsList = new ArrayList();
        for (int u = 0; u < segmt.segmentedBeats.size(); u++) {
            Observation obs = (Observation) segmt.segmentedBeats.get(u);
            int size = obs.getSign().size();
            // Lets have no more than 20 Observations
//            float fact = (float) (size / 20.0) ;
            int deux = (int) (size % 20);
            String suffix = String.valueOf(deux);
            obs.setNameSufx(suffix);
            obsList.add(obs);
        }

        goglEP.hmmTest = new HMM(obsList);
        goglEP.hmmTest.train();

        Viterbi vt = new Viterbi();

        similarity = vt.viterbi();
        goglEP.hmmTest.setSimilarity(similarity);

    }
}
