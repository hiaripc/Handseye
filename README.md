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
La YOLOv5 è implementata con Pytorch. 
Non capisco se sia di facto "indispensabile" o solo un modo di approcciare, però mi sembra il più semplice.

Per ora, immagino la possibilità di usare YOLO per trovare la mano, poi passare il frame tagliato alla nostra CNN home made.

![alt text](https://miro.medium.com/max/696/1*_qslg8EKUDPhin0nVum_ug.png)

