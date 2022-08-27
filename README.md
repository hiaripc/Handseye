# Handseye
Handseye is an Android App (in development) which uses AI techniques to output the letters, represented with human hands, perceived by the smartphone's camera.

## Butto giù tutto quello che mi passa per la mente:
Il primo tentativo di costruzione della CNN (nella cartella c'è un README che la riassume) riesce a riconoscere con una teorica accuratezza del 100% le lettere dalle mani, isolate. Una principale problematica sarà il dover riconoscere e localizzare la mano all'interno di un immagine che non contiene solo la mano (una persona intera? più persone? vogliamo un use case preciso o espanderlo a più casi possibili?), per poi darla alla cnn che è solo un puro riconoscitore. 
Bisognerà capire se è l'approccio giusto e come fare ciò.
Per ora immagino una sorta di due fasi, prima un filtro che localizza la mano (o le mani) poi la cnn.

Leggendo [questo articolo]( https://appsilon.com/object-detection-yolo-algorithm/#:~:text=YOLO%20%E2%80%9CYou%20Only%20Look%20Once,by%20Joseph%20Redmon%20et%20al ) mi sembra di capire che questo sia un approccio un po' meh. Ovvero sarebbe un approccio basato sulla classificazione.
A quanto pare un altro approccio più semplice è dato dalla regressione, che permette di trovare gli elementi d'interesse nell'immagine direttamente. L'algoritmo YOLO, che sempre una rete neurale è, (che in un'unica iterazione fa l'object detection) è esattamente uno di questi algoritmi, che potremmo provare a usare. Darknet è una delle sue implementazioni open source.
La YOLOv5 è implementata con Pytorch, una sua implementazione opensource è data da [ultralytics]( https://docs.ultralytics.com/).
[Installazione](https://pytorch.org/hub/ultralytics_yolov5/), all'interno trovi un file jupyter notebook di tutorial.

P.s.....  ENVIRONMENT....... occhio a scheda grafica, ho dovuto provare tutte le possibili combinazioni possibili prima di trovare un cudatoolkit che funzionasse. Per me, che ho la versione cuda 11.4 (controlla con comando nvidia-smi) è funzionato l'11.3. Comunque consiglio molto di fare ambiente conda a parte. Inoltre ti consiglio di fare tutto in un bel conda apposito per l'occasione. Altra problematica: jupyter notebook potrebbe avere dei problemi a riconoscerti i moduli installati nell'ambiente conda, prova [qui](https://stackoverflow.com/questions/39604271/conda-environments-not-showing-up-in-jupyter-notebook). Il vantaggio di usare jupyter sta nel poter eseguire singole parti del codice python separatamente tenendo in memoria le altre, niente male se vuoi lavorare sui risultati senza doverti rifare tutto il train.

Non capisco se sia di facto "indispensabile" o solo un modo di approcciare, però mi sembra il più semplice.

Per ora, immagino la possibilità di usare YOLO per trovare la mano, poi passare il frame tagliato alla nostra CNN home made.

![alt text](https://miro.medium.com/max/696/1*_qslg8EKUDPhin0nVum_ug.png)


Su Roboflow ho trovato [un progetto]( https://github.com/insigh1/Interactive_ABCs_with_American_Sign_Language_using_Yolov5) che di facto fa quello che vorremmo fare. 

Penso che studiarcelo sia un ottima cosa da fare, anche perchè comunque a noi mancherà tutto il porting su android che non penso sia da poco.. Inoltre dalle conclusioni a cui è arrivato pare che il tutto non sia così facile. Il suo modello ha comunque molte difficoltà ad identificare mani che non siano su uno sfondo molto bianco. Potremmo provare a prendere qualche spunto e tentare di migliorare quel che riusciamo.
Da notare che il suo dataset era molto distretto (720 immagini, che ha aumentato ricavandone 25 in più da ognuna con data augmentation), magari noi con dataset più grandi potremmo ottenere risultati migliori. Fa però notare che i dataset trovati avevano basse definizioni, per questo lui se lo è creato con aiuto dei social.

P.s. Roboflow è un sito che permette anche di crearsi i propri dataset e di fare image labeling, vedo che diversi articoli che incontro lo usano, magari ci tornerà utile.

Da un primissimo approccio un po' random a Yolov5 utilizzando la webcam non riconosce le mani a sè, ma come persona. Le dita poi spesso sono spazzolini, coltelli o hotdog. Penso che di base sia allenata su COCO dataset, che ha un certo numero di classi generiche, ma sicuramente ci sarà da allenarla con un bel dataset.

[Un paper carino](https://www.researchgate.net/profile/Eleas-Ahmed/publication/353489194_Using_YOLOv5_Algorithm_to_Detect_and_Recognize_American_Sign_Language/links/61c066c5fd2cbd7200b26ebb/Using-YOLOv5-Algorithm-to-Detect-and-Recognize-American-Sign-Language.pdf) da cui mi sembra di capire che l'approccio con Yolo sia effettivamente il più saggio essendo veloce e leggero (quindi anche deloyable su smartphone).

Qui usano un approccio totalmente basato su Yolo inquadrando però direttamente la sola mano, quindi non per object detection della mano (non so se funzionerebbe anche su immagini più lontane, non credo) ma per riconoscere il simbolo che rappresenta, più semplice del nostro use case. Andrà capito se conviene usare una Yolo anche a valle per capire il simbolo o usarla solo per passare la patch della manina generica a una nostra CNN apposita.

Potremmo anche pensare di creare un nostro semplice dataset per semplificarci un po' la vita come fa [questo simpatico indiano]( https://www.youtube.com/watch?v=1amn2nlYdSs). Si filma dalla webcam mentre fa i segni, poi frame per frame fa il labelling e poi si allena la sua Yolo. Penso che sia un approccio molto semplice ma molto specifico, che potrebbe avere problemi di adattamento ad altri mani/persone/ambienti. Potrebbe però essere una possibile strada. 
In realtà anche il primo esempio non ha un approccio molto differente.

[Guida](https://towardsdatascience.com/the-practical-guide-for-object-detection-with-yolov5-algorithm-74c04aac4843)per il training di Yolov5, per capire meglio: [differenza fra batch ed epoch, SGD](https://machinelearningmastery.com/difference-between-a-batch-and-an-epoch/), a proposito di batch size: va bene mettere il valore più alto che l'hardware regga a livello di memoria, per me 16 con dataset roboflow mi riempiono già un 90% di ram (da 16GB). 
Leggendo, si capisce che si può procedere o facendo un training da zero, migliore se si ha un buon dataset, oppure partire da un modello (passandolo in input come i pesi iniziali) e modificarlo. Anche qui ci sarà da scegliere in base a ciò che faremo.

Ora 12.30 del 27/08 ho iniziato il training della rete, 150 epoche. Ci metterò qualche ora mi sa.... 

