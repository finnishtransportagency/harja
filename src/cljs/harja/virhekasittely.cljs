(ns harja.virhekasittely
  (:require [reagent.core :refer [atom]]
            [harja.ui.palaute :as palaute]))

(def pahoitteluviesti
  "Hupsista, Harja räsähti. Olemme pahoillamme. Kuulisimme mielellämme miten sait vian esiin. Klikkaa tähän, niin näet tekniset tiedot ongelmasta.")

(defn rendaa-virhe [e]
  (let [auki (atom false)
        virhe-str (fn [e] (if (instance? js/Error e)
                            (.-stack e)
                            e))]
    (fn [e]
      [:div.crash-component {:on-click #(swap! auki not)}
       [palaute/virhe-palaute (virhe-str e)]
       [:span pahoitteluviesti]
       [:div.crash-details {:class (if @auki "details-open" "")}
        (virhe-str e)]])))

(defn arsyttava-virhe [& msgs]
  (.alert js/window (str "Upsista keikkaa. Voisitko lähettää meille palautetta, ja liittää mukaan kuvakaappauksen tai alla olevan tekstin, sekä kuvauksen siitä mitä olit tekemässä.

  " (apply str msgs))))
