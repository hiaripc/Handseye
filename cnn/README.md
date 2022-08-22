# Dataset kaggle (first try, cnn creata con codice allegato a dataset)
https://www.kaggle.com/datasets/grassknoted/asl-alphabet

Nella cartella kaggle trovi diversi file:
    - cartella input: all'interno ci sono i dataset in formato csv, sia per test che per training.
    - cnn-using-keras...ipynb crea la cnn, è un file jupyter molto dettagliato che spiega bene come e cosa fa. Ho giusto apportato un paio di modifiche per adattarlo alle versioni più recenti di tensorflow, da guardare. Questo file comunque se lo runni ti dà un po' di info simpatiche. 
    Crea la rete appunto che è il file keras_cnn.h5, non serve ricrearla rifacendone il run.
    - cnn_webcam.py è un file in cui faccio un primo tentativo di usare la cnn da webcam del computer, se lo apri e esegui dovrebbe partire senza problemi. L'immagine diventa a colori quando riconosce un qualcosa... non mi succede spesso.

Problematiche: 
Facendo qualche test con la webcam il risultato non migliorava di molto, avevo un buon feedback molto raramente. Ho ipotizzato che uno dei motivi potesse essere che la rete è allenata su immagini 28x28 pixel, quindi piccolissime. Probabilmente quindi le mani  devono essere molto in chiaro su sfondo bianco... poco adatto. In più sarebbe necessario un ulteriore layer che "zoommi" sulla mano per una cnn così specifica e poco sensibile.

## Dataset roboflox
https://public.roboflow.com/object-detection/american-sign-language-letters
Il video è molto più promettente.

