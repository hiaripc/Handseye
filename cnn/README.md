# Dataset kaggle (first try, cnn creata con codice allegato a dataset)
[(87,000 immagini 200x200)](https://www.kaggle.com/datasets/grassknoted/asl-alphabet)
Scaricato come csv, per Tensorflow.
Nella cartella kaggle trovi diversi file:
    - cartella input: all'interno ci sono i dataset in formato csv, sia per test che per training.
    - cnn-using-keras...ipynb crea la cnn, è un file jupyter molto dettagliato che spiega bene come e cosa fa. Ho giusto apportato un paio di modifiche per adattarlo alle versioni più recenti di tensorflow, da guardare. Questo file comunque se lo runni ti dà un po' di info simpatiche. 
    Crea la rete appunto che è il file keras_cnn.h5, non serve ricrearla rifacendone il run.
    - webcam_test.py è un file in cui faccio un primo tentativo di usare la cnn da webcam del computer, se lo apri e esegui dovrebbe partire senza problemi. L'immagine diventa a colori quando riconosce un qualcosa... non mi succede spesso.

Problematiche: 
Facendo qualche test con la webcam il risultato non migliorava di molto, avevo un buon feedback molto raramente. Ho ipotizzato che uno dei motivi potesse essere che la rete è allenata su immagini 28x28 pixel, quindi piccolissime. Probabilmente quindi le mani  devono essere molto in chiaro su sfondo bianco... poco adatto. In più sarebbe necessario un ulteriore layer che "zoommi" sulla mano (object detenction) per una cnn così specifica e poco sensibile.
NB! Sono scelte fatte a livello di codice nel creare la rete, non proprie del dataset. Intanto lo cambio solo per cercare di capire come funziona da esempi spiegati.  


## Dataset roboflox
[(1,700 immagini)](https://public.roboflow.com/object-detection/american-sign-language-letters). L'ho scaricato come Yolov5 OBB (oriented bounding boxes TXT annotations), ci sono tutte le immagini, le labels ed il file conf yaml. 

Non sapevo se inserire YOLOv5 come [git submodule](https://git-scm.com/book/en/v2/Git-Tools-Submodules) perchè è presa da git. Se modifichiamo qualcosa all'interno poi dove caricha i push? Non so come fare bene in realtà, forse è meglio tenerla fuori dal progetto.

Il file Handseye.ipynb è una prima prova, va lanciato dentro la cartella yolo5.



