package com.example.myapplication.transcriptionService;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class MelSpectrogram {
    private float sampleRate = 16000;
    private int n_fft = 512;
    private int hop_length = 160;
    private int n_mels = 80;
    private double fMin = 0.0;
    private double fMax = sampleRate / 2.0;


    /**
     * This function generates mel spectrogram values
     *
     * @param y
     * @return
     */
    public double[][] melSpectrogram(float[] y) {
        double[][] melBasis = melFilter();
        double[][] spectro = extractSTFTFeatures(y);
        double[][] melS = new double[melBasis.length][spectro[0].length];
        for (int i = 0; i < melBasis.length; i++) {
            for (int j = 0; j < spectro[0].length; j++) {
                for (int k = 0; k < melBasis[0].length; k++) {
                    melS[i][j] += melBasis[i][k] * spectro[k][j];
                }
            }
        }
        return melS;
    }

    /**
     * This function is used to create a Filterbank matrix to combine FFT bins into
     * Mel-frequency bins.
     *
     * @return
     */
    private double[][] melFilter() {
        // Create a Filterbank matrix to combine FFT bins into Mel-frequency bins.
        // Center freqs of each FFT bin
        final double[] fftFreqs = fftFreq();
        // 'Center freqs' of mel bands - uniformly spaced between limits
        final double[] melF = melFreq(n_mels + 2);

        double[] fdiff = new double[melF.length - 1];
        for (int i = 0; i < melF.length - 1; i++) {
            fdiff[i] = melF[i + 1] - melF[i];
        }

        double[][] ramps = new double[melF.length][fftFreqs.length];
        for (int i = 0; i < melF.length; i++) {
            for (int j = 0; j < fftFreqs.length; j++) {
                ramps[i][j] = melF[i] - fftFreqs[j];
            }
        }

        double[][] weights = new double[n_mels][1 + n_fft / 2];
        for (int i = 0; i < n_mels; i++) {
            for (int j = 0; j < fftFreqs.length; j++) {
                double lowerF = -ramps[i][j] / fdiff[i];
                double upperF = ramps[i + 2][j] / fdiff[i + 1];
                if (lowerF > upperF && upperF > 0) {
                    weights[i][j] = upperF;
                } else if (lowerF > upperF && upperF < 0) {
                    weights[i][j] = 0;
                } else if (lowerF < upperF && lowerF > 0) {
                    weights[i][j] = lowerF;
                } else if (lowerF < upperF && lowerF < 0) {
                    weights[i][j] = 0;
                } else {
                }
            }
        }

        double enorm[] = new double[n_mels];
        for (int i = 0; i < n_mels; i++) {
            enorm[i] = 2.0 / (melF[i + 2] - melF[i]);
            for (int j = 0; j < fftFreqs.length; j++) {
                weights[i][j] *= enorm[i];
            }
        }
        return weights;

        // need to check if there's an empty channel somewhere
    }

    /**
     * To get fft frequencies
     *
     * @return
     */
    private double[] fftFreq() {
        // Alternative implementation of np.fft.fftfreqs
        double[] freqs = new double[1 + n_fft / 2];
        for (int i = 0; i < 1 + n_fft / 2; i++) {
            freqs[i] = 0 + (sampleRate / 2) / (n_fft / 2) * i;
        }
        return freqs;
    }

    /**
     * To get mel frequencies
     *
     * @param numMels
     * @return
     */
    private double[] melFreq(int numMels) {
        // 'Center freqs' of mel bands - uniformly spaced between limits
        double[] LowFFreq = new double[1];
        double[] HighFFreq = new double[1];
        LowFFreq[0] = fMin;
        HighFFreq[0] = fMax;
        final double[] melFLow = freqToMel(LowFFreq);
        final double[] melFHigh = freqToMel(HighFFreq);
        double[] mels = new double[numMels];
        for (int i = 0; i < numMels; i++) {
            mels[i] = melFLow[0] + (melFHigh[0] - melFLow[0]) / (numMels - 1) * i;
        }
        return melToFreq(mels);
    }

    /**
     * To convert hz frequencies into mel frequencies
     *
     * @param freqs
     * @return
     */
    protected double[] freqToMel(double[] freqs) {
        final double f_min = 0.0;
        final double f_sp = 200.0 / 3;
        double[] mels = new double[freqs.length];

        // Fill in the log-scale part

        final double min_log_hz = 1000.0; // beginning of log region (Hz)
        final double min_log_mel = (min_log_hz - f_min) / f_sp; // # same (Mels)
        final double logstep = Math.log(6.4) / 27.0; // step size for log region

        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] < min_log_hz) {
                mels[i] = (freqs[i] - f_min) / f_sp;
            } else {
                mels[i] = min_log_mel + Math.log(freqs[i] / min_log_hz) / logstep;
            }
        }
        return mels;
    }

    /**
     * To convert mel frequencies into hz frequencies
     *
     * @param mels
     * @return
     */
    private double[] melToFreq(double[] mels) {
        // Fill in the linear scale
        final double f_min = 0.0;
        final double f_sp = 200.0 / 3;
        double[] freqs = new double[mels.length];

        // And now the nonlinear scale
        final double min_log_hz = 1000.0; // beginning of log region (Hz)
        final double min_log_mel = (min_log_hz - f_min) / f_sp; // same (Mels)
        final double logstep = Math.log(6.4) / 27.0;

        for (int i = 0; i < mels.length; i++) {
            if (mels[i] < min_log_mel) {
                freqs[i] = f_min + f_sp * mels[i];
            } else {
                freqs[i] = min_log_hz * Math.exp(logstep * (mels[i] - min_log_mel));
            }
        }
        return freqs;
    }

    /**
     * This function extract STFT values from given Audio Magnitude Values.
     *
     * @param y
     * @return
     */
    public double[][] extractSTFTFeatures(float[] y) {
        // Short-time Fourier transform (STFT)
        final double[] fftwin = getWindow();

        // pad y with reflect mode so it's centered. This reflect padding implementation
        // is
        final double[][] frame = padFrame(y, true);
        double[][] fftmagSpec = new double[1 + n_fft / 2][frame[0].length];

        double[] fftFrame = new double[n_fft];

        for (int k = 0; k < frame[0].length; k++) {
            int fftFrameCounter = 0;
            for (int l = 0; l < n_fft; l++) {
                fftFrame[fftFrameCounter] = fftwin[l] * frame[l][k];
                fftFrameCounter = fftFrameCounter + 1;
            }

            double[] tempConversion = new double[fftFrame.length];
            double[] tempImag = new double[fftFrame.length];

            FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);



            try {
                Complex[] complx = transformer.transform(fftFrame, TransformType.FORWARD);

                for (int i = 0; i < complx.length; i++) {
                    double rr = (complx[i].getReal());

                    double ri = (complx[i].getImaginary());

                    tempConversion[i] = rr * rr + ri*ri;
                    tempImag[i] = ri;
                }

            } catch (IllegalArgumentException e) {
                System.out.println(e);
            }



            double[] magSpec = tempConversion;
            for (int i = 0; i < 1 + n_fft / 2; i++) {
                fftmagSpec[i][k] = magSpec[i];
            }
        }
        return fftmagSpec;
    }

    /**
     * This function is used to get hann window, librosa
     *
     * @return
     */
    private double[] getWindow() {
        // Return a Hann window for even n_fft.
        // The Hann window is a taper formed by using a raised cosine or sine-squared
        // with ends that touch zero.
        double[] win = new double[n_fft];
        for (int i = 0; i < n_fft; i++) {
            win[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / n_fft);
        }
        return win;
    }

    /**
     * This function pads the y values
     *
     * @param yValues
     * @return
     */

    private double[][] padFrame(float[] yValues, boolean paddingFlag){

        double[][] frame = null;

        if(paddingFlag) {


            double[] ypad = new double[n_fft + yValues.length];
            for (int i = 0; i < n_fft / 2; i++) {
                ypad[(n_fft / 2) - i - 1] = yValues[i + 1];
                ypad[(n_fft / 2) + yValues.length + i] = yValues[yValues.length - 2 - i];
            }
            for (int j = 0; j < yValues.length; j++) {
                ypad[(n_fft / 2) + j] = yValues[j];
            }

            frame = yFrame(ypad);
        }
        else {


            double[] yDblValues = new double[yValues.length];
            for (int i = 0 ; i < yValues.length; i++)
            {
                yDblValues[i] = (double) yValues[i];
            }

            frame = yFrame(yDblValues);

        }

        return frame;
    }

    /**
     * This function is used to apply padding and return Frame
     *
     * @param ypad
     * @return
     */
    private double[][] yFrame(double[] ypad) {

        final int n_frames = 1 + (ypad.length - n_fft) / hop_length;

        double[][] winFrames = new double[n_fft][n_frames];

        for (int i = 0; i < n_fft; i++) {
            for (int j = 0; j < n_frames; j++) {
                winFrames[i][j] = ypad[j * hop_length + i];
            }
        }
        return winFrames;
    }

}