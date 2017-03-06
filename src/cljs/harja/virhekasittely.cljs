(ns harja.virhekasittely
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.palaute :as palaute]
            [harja.ui.ikonit :as ikonit]))

(def pahoitteluviesti
  "Hupsista, Harja räsähti! Olemme pahoillamme. Kuulisimme mielellämme miten sait vian esiin. Klikkaa tähän, niin pääset lähettämään virheraportin.")

(defn rendaa-virhe [e]
  (let [auki (atom false)
        virhe-str (fn [e] (if (instance? js/Error e)
                            (.-stack e)
                            e))]
    (fn [e]
      [:div.crash-component {:on-click #(swap! auki not)}
       [:a
        {:href (palaute/mailto-linkki (palaute/mailto-kehitystiimi)
                                      (palaute/palaute-body-virhe (virhe-str e))
                                      "Kaatuminen")
         :on-click #(.stopPropagation %)}
        (ikonit/ikoni-ja-teksti (ikonit/envelope) pahoitteluviesti)]
       [:div.crash-details {:class (if @auki "details-open" "")}
        (virhe-str e)]])))

(defn arsyttava-virhe [& msgs]
  (.alert js/window (str "Upsista keikkaa, Harja räsähti! Olemme pahoillamme. Kuulisimme "
                         "mielellämme miten sait vian esiin, joten voisitko lähettää meille "
                         "palautetta? Liitä mukaan alla olevat virheen tekniset tiedot, "
                         "kuvankaappaus sekä kuvaus siitä mitä olit tekemässä.\n"
                         (apply str msgs))))
