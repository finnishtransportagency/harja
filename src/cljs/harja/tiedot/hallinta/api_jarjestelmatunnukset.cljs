(ns harja.tiedot.hallinta.api-jarjestelmatunnukset
  "Hallinnoi integraatiolokin tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.organisaatiot :refer [organisaatiot]]
            [harja.atom :refer [paivita!]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce nakymassa? (atom false))

(defonce jarjestelmatunnukset
  (reaction<! [nakymassa? @nakymassa?]
              (when nakymassa?
                (k/post! :hae-jarjestelmatunnukset nil))))

(defn organisaatiovalinnat []
  (distinct (map #(select-keys % [:id :nimi]) @organisaatiot)))

(defonce urakkavalinnat
  (reaction<! [nakymassa? @nakymassa?
               oikeus? (oikeudet/hallinta-api-jarjestelmatunnukset)]
              (when (and nakymassa? oikeus?)
                (k/post! :hae-urakat-lisaoikeusvalintaan nil))))

(defn- tallenna-jarjestelmatunnukset [muuttuneet-tunnukset]
  (go (let [uudet-tunnukset (<! (k/post! :tallenna-jarjestelmatunnukset
                                         muuttuneet-tunnukset))]
        (reset! jarjestelmatunnukset uudet-tunnukset))))

(defn- tallenna-jarjestelmatunnuksen-lisaoikeudet [muuttuneet-oikeudet kayttaja-id tulos-atom]
  (go (let [uudet-oikeudet (<! (k/post! :tallenna-jarjestelmatunnuksen-lisaoikeudet
                                        {:oikeudet muuttuneet-oikeudet
                                         :kayttaja-id kayttaja-id}))]
        (reset! tulos-atom uudet-oikeudet))))

(defn hae-jarjestelmatunnuksen-lisaoikeudet [kayttaja-id tulos-atom]
  (go (let [oikeudet (<! (k/post! :hae-jarjestelmatunnuksen-lisaoikeudet {:kayttaja-id kayttaja-id}))]
        (reset! tulos-atom oikeudet))))
