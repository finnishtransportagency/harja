(ns harja-laadunseuranta.ui.ilmoitukset
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.tiedot.ilmoitukset :as tiedot]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn ilmoituskomponentti
  "Piirtää nykyisen ilmoituksen"
  [{:keys [ilmoitus-atom lomakedata havainnon-id
           taydenna-havaintoa-painettu-fn ilmoitukseen-liittyva-havainto-id-atom] :as tiedot}]
  (let [piirrettava-ilmoitus (atom @ilmoitus-atom)]
    (fn [{:keys [ilmoitus-atom lomakedata havainnon-id
                 taydenna-havaintoa-painettu-fn ilmoitukseen-liittyva-havainto-id-atom] :as tiedot}]

      (let [taydennettava? (boolean
                             (and lomakedata havainnon-id (not= (:tyyppi @ilmoitus-atom) :virhe)))]
        (if (and (not= piirrettava-ilmoitus @ilmoitus-atom)
                 (not (str/blank? (:ilmoitus @ilmoitus-atom))))
          ;; kun saadaan uusi ilmoitus, vaihdetaan se käytöön.
          ;; muuten piirretään aina vanha jotta transitiossa alkaessa sisältö ei katoa
          (reset! piirrettava-ilmoitus @ilmoitus-atom))
        (when @ilmoitus-atom
          (tiedot/tyhjenna-ilmoitus-nakymisajan-jalkeen @ilmoitus-atom ilmoitus-atom
                                                        ilmoitukseen-liittyva-havainto-id-atom))

        [:div.ilmoitukset {:class (str (when-let [tyyppi (:tyyppi @piirrettava-ilmoitus)]
                                         (str "ilmoitus-tyyppi-" (name tyyppi)))
                                       (when-not @ilmoitus-atom
                                         " ilmoitus-container-piilossa ")
                                       (when taydennettava?
                                         " klikattava "))
                           :on-click (when taydennettava?
                                       taydenna-havaintoa-painettu-fn)}
         [:div {:class (str "ilmoitus ")}
          (:ilmoitus @piirrettava-ilmoitus)]
         (when taydennettava?
           [:a#taydenna-havaintoa
            "Täydennä havaintoa"])]))))