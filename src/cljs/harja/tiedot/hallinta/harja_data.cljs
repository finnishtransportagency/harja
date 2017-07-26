(ns harja.tiedot.hallinta.harja-data
  "Hallinnoi harja-datan tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [Event process-event] :as tuck])

  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce app (atom {:analyysit ["yhteyskatkokset" "kaytto"]
                    :valittu-analyysi nil
                    :nakymassa? false
                    :yhteyskatkokset-pvm-data {}
                    :yhteyskatkokset-palvelut-data {}
                    :yhteyskatkosryhma-pvm-data {}
                    :yhteyskatkosryhma-palvelut-data {}
                    :yhteyskatkokset-jarjestykset ["pvm" "palvelut"]
                    :yhteyskatkokset-arvot ["katkokset" "katkosryhmät"]
                    :valittu-yhteyskatkokset-jarjestys nil
                    :valittu-yhteyskatkokset-arvo "katkokset"
                    :arvoa-vaihdetaan? false
                    :hakuasetukset-nakyvilla? false
                    :haku-kaynnissa #{:alku}
                    :kaytossa {:min-katkokset 0
                               :naytettavat-ryhmat #{:tallenna :hae :urakan :muut}}
                    :hakuasetukset {:min-katkokset 0
                                    :naytettavat-ryhmat #{:tallenna :hae :urakka :muut}}}))

(defn graylog-palvelukutsu
  "Hakee serveriltä yhteyskatkosdatan."
  [palvelu callback hakuasetukset]
  (go (try (let [data (<! (k/post! palvelu hakuasetukset))]
              (callback data))
           (catch :default e
              (log (pr-str e))))))
(defn graylog-haku
  [haku {ryhma-avain :ryhma-avain jarjestys-avain :jarjestys-avain} app]
  (let [hakukoodi (gensym (name haku))
        naytettavat-ryhmat (get-in app [:hakuasetukset :naytettavat-ryhmat])
        min-katkokset (get-in app [:hakuasetukset :min-katkokset])]
    (graylog-palvelukutsu (keyword (str "graylog-hae-" (name haku)))
                          (tuck/send-async! ->PaivitaYhteyskatkosArvot hakukoodi (keyword (str (name haku) "-" (name jarjestys-avain) "-data")))
                          {:ryhma-avain ryhma-avain
                           :jarjestys-avain jarjestys-avain
                           :naytettavat-ryhmat naytettavat-ryhmat
                           :min-katkokset min-katkokset})
    (update app :haku-kaynnissa #(conj % hakukoodi))))

(defrecord PaivitaArvo [arvo avain])
(defrecord Nakymassa? [nakymassa?])
(defrecord HaeYhteyskatkosData [ryhma-avain jarjestys-avain])
(defrecord HaeYhteyskatkosryhmaData [ryhma-avain jarjestys-avain])
(defrecord PaivitaYhteyskatkosArvot [data hakukoodi app-avain])
(defrecord PaivitaArvoFunktio [funktio avain])

(extend-protocol Event
  PaivitaArvo
  (process-event [{:keys [arvo avain]} app]
    (assoc app avain arvo))
  PaivitaArvoFunktio
  (process-event [{:keys [funktio avain]} app]
    (update app avain #(funktio %)))
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))
  HaeYhteyskatkosData
  (process-event [parametrit app]
    (graylog-haku :yhteyskatkokset parametrit app))
  HaeYhteyskatkosryhmaData
  (process-event [parametrit app]
    (graylog-haku :yhteyskatkosryhma parametrit app))
  PaivitaYhteyskatkosArvot
  (process-event [{data :data hakukoodi :hakukoodi app-avain :app-avain} app]
    (assoc app app-avain data :haku-kaynnissa (disj (:haku-kaynnissa app) hakukoodi :alku))))
