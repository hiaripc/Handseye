import cv2
import numpy as np
from PIL import Image
from keras import models

#Load the saved model
model = models.load_model('keras_cnn.h5')

print(str(model.layers[0].input_shape))
print(str(model.summary()))

video = cv2.VideoCapture(0)
f = open ("output.txt", "w")
c = 0
while True:
        _, frame = video.read()

        #Convert the captured frame into RGB
        im = Image.fromarray(frame, 'RGB')

        #Resizing into 28x28 because we trained the model with this image size.
        im = im.resize((28,28))
        img_array = np.array(im)
        #Our keras model used a 4D tensor, (images x height x width x channel)
        # Converting from 3 to 1 channel
        gray_image = cv2.cvtColor(img_array, cv2.COLOR_RGB2GRAY)
        img_array = gray_image
        #So changing dimension 28x28x1 1x28x28x1 
        img_array = np.expand_dims(img_array, axis=0)

        #Saving the image passed to the cnn in webcam_img/ to check.
        #Comment this if not interessed  
        img_name = "webcam_img/img_{}.jpg".format(c)
        cv2.imwrite(img_name, gray_image)
        c+=1

        #Calling the predict method on model to predict on the image
        prediction = model.predict(img_array)
        prediction_int = int(prediction[0][0])

        #if prediction is 0 aka the CNN is not get it then show the frame in gray color.
        if prediction_int == 0:
                frame = cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY)

        #Writing down prediction value in output file. Should be a number between 0 and 1
        f.write('{}\n'.format(prediction_int))
        cv2.imshow("Capturing", frame)
        key=cv2.waitKey(1)
        if key == ord('q'):
                break
f.close
video.release()
cv2.destroyAllWindows()

