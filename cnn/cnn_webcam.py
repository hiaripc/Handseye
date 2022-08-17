import cv2
import numpy as np
from PIL import Image
from keras import models

#Load the saved model
model = models.load_model('keras_cnn.h5')
video = cv2.VideoCapture(0)

while True:
        _, frame = video.read()

        #Convert the captured frame into RGB
        im = Image.fromarray(frame, 'RGB')

        #Resizing into 28x28 because we trained the model with this image size.
        im = im.resize((28,28))
        img_array = np.array(im)
        #Our keras model used a 4D tensor, (images x height x width x channel)
        # Converting to 1 channel
        gray_image = cv2.cvtColor(img_array, cv2.COLOR_BGR2GRAY)
        img_array = gray_image
        #So changing dimension 28x28x1 1x28x28x1 
        img_array = np.expand_dims(img_array, axis=0)

        #Calling the predict method on model to predict 'me' on the image
        prediction = int(model.predict(img_array)[0][0])

        #if prediction is 0, which means I am missing on the image, then show the frame in gray color.
        if prediction == 1:
                frame = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)


        cv2.imshow("Capturing", frame)
        key=cv2.waitKey(1)
        if key == ord('q'):
                break
video.release()
cv2.destroyAllWindows()