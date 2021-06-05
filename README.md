# smarter-launcher (WIP)
An Android launcher which understands your usage patterns and updates the Home screen with a list of 
apps which are most likely to be used by you next. This project uses the algorithm introduced in 
the paper titled "Mobile App Recommendation with Sequential App Usage Behavior Tracking" (link below).

## How it works?
The app reads the system/context variables (like time of day, earphone plugged in or not, bluetooth &
network status etc) and represents them as a real valued vector every time an app is launched. These
vectors are stored as "history". On every new app launch KNN (K Nearest Neighbour) is performed to find
the closest vectors and corresponding apps. The top most similar apps are suggested to the user.  

More details are available on ![my blog here](https://asutoshnayak.medium.com/building-android-smart-launcher-with-machine-learning-929dda7f107)

See Smarter Launcher in action:

![wep_small](https://user-images.githubusercontent.com/25876491/120836299-0cbc8080-c583-11eb-8c4a-b951b2998d27.gif)

![woep_small](https://user-images.githubusercontent.com/25876491/120836455-37a6d480-c583-11eb-863f-dc38d979d03a.gif)

Note: The wallpaper is not part of the launcher. It's a Live Wallpaper I had developed last year. It's on PlayStore ![Matrix LiveWallpaper](https://play.google.com/store/apps/details?id=com.outliers.matrixlivewallpaper).

## Credits
* The algorithm was introduced in the paper: https://jit.ndhu.edu.tw/article/viewFile/2061/2073
* App logo is designed by combining the logos from FlatIcons. Links to authors' page:

  https://www.flaticon.com/authors/smashicons
  
  https://www.flaticon.com/authors/prettycons
  
  Huge shoutout to the authors!



