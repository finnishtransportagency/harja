(ns harja-laadunseuranta.mock.geolocation)

(defn setup-mock-geolocation!
  ([] (setup-mock-geolocation! #js {:coords #js {:latitude 1
                                                 :longitude 2
                                                 :accuracy 5
                                                 :altitude 10
                                                 :altitudeAccuracy 5
                                                 :heading 45
                                                 :speed 10}
                                    :timestamp 429843}))
  ([result]
   (set! (.-geolocation js/navigator)
         #js {:watchPosition (fn [ok err opts]
                               (js/setInterval #(ok result)))
              :clearWatch (fn [watch]
                            (js/clearInterval watch))
              :getCurrentPosition (fn [ok err opts]
                                    (ok result))})))
