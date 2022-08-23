# Handseye
Handseye is an Android App (in development) which uses AI techniques to output the letters, represented with human hands, perceived by the smartphone's camera.

Considerazioni:
Il primo tentativo di costruzione della CNN (nella cartella c'è un README che la riassume) riesce a riconoscere con una teorica accuratezza del 100% le lettere dalle mani, isolate. Una principale problematica sarà il dover riconoscere e localizzare la mano all'interno di un immagine che non contiene solo la mano (una persona intera? più persone? vogliamo un use case preciso o espanderlo a più casi possibili?), per poi darla alla cnn che è solo un puro riconoscitore. 
Bisognerà capire se è l'approccio giusto e come fare ciò.
Per ora immagino una sorta di due fasi, prima un filtro che localizza la mano (o le mani) poi la cnn.

Leggendo:

 https://appsilon.com/object-detection-yolo-algorithm/#:~:text=YOLO%20(%E2%80%9CYou%20Only%20Look%20Once,by%20Joseph%20Redmon%20et%20al 

mi sembra di capire che questo sia un approccio un po' meh. Ovvero sarebbe un approccio basato sulla classificazione.
A quanto pare un altro approccio più semplice è dato dalla regressione, che permette di trovare gli elementi d'interesse nell'immagine direttamente. L'algoritmo YOLO (che in un'unica iterazione fa l'object detection) è esattamente uno di questi algoritmi, che potremmo provare a usare. Darknet è una delle sue implementazioni open source.
La YOLOv5 è implementata con Pytorch, una sua implementazione opensource è data da ultralytics.
 https://docs.ultralytics.com/

Installazione:
 https://pytorch.org/hub/ultralytics_yolov5/

P.s. .... occhio a scheda grafica, ho dovuto provare tutte le possibili combinazioni possibili prima di trovare un cudatoolkit che funzionasse. Per me, che ho la versione cuda 11.4 (controlla con comando nvidia-smi) è funzionato l'11.3. Comunque consiglio molto di fare ambiente conda a parte.

Non capisco se sia di facto "indispensabile" o solo un modo di approcciare, però mi sembra il più semplice.

Per ora, immagino la possibilità di usare YOLO per trovare la mano, poi passare il frame tagliato alla nostra CNN home made.

![alt text](https://miro.medium.com/max/696/1*_qslg8EKUDPhin0nVum_ug.png)


Ho trovato un progetto che di facto fa quello che vorremmo fare. 
 https://github.com/insigh1/Interactive_ABCs_with_American_Sign_Language_using_Yolov5

Penso che studiarcelo sia un ottima cosa da fare, anche perchè comunque a noi mancherà tutto il porting su android che non penso sia da poco.. Inoltre dalle conclusioni a cui è arrivato pare che il tutto non sia così facile. Il suo modello ha comunque molte difficoltà ad identificare mani che non siano su uno sfondo molto bianco. Potremmo provare a prendere qualche spunto e tentare di migliorare quel che riusciamo.
Da notare che il suo dataset era molto distretto (720 immagini, che ha aumentato ricavandone 25 in più da ognuna con data augmentation), magari noi con dataset più grandi potremmo ottenere risultati migliori.

