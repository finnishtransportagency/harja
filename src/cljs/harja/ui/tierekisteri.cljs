(ns harja.ui.tierekisteri)
"Uudelleenkäytettävä komponentti tierekisteritietojen näyttämiseen."

(defn tierekisteri
  [numero aosa aet losa lopet]
  (let [laita (fn [arvo]
                (if (or
                      (and (number? arvo) (not (nil? arvo)))
                      (not (empty? arvo))) arvo "?"))]
       [:span (str (laita numero) " / " (laita aosa) " / " (laita aet) " / " (laita losa) " / " (laita lopet))]))