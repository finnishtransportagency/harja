(ns harja.tiedot.hallinta.harja-data
  "Hallinnoi harja-datan tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [cljs-time.core :as time]
            [harja.atom :refer [paivita!]]
            [cljs-time.core :as t]
            [harja.pvm :as pvm]
            [clojure.string :as st]
            [tuck.core :refer [Event process-event] :as tuck]
            [harja.domain.graylog :as dgl]
            [cljs.spec.alpha :as s]
            [cljs.spec.gen.alpha :as gen])

  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce app (atom {:analyysit ["yhteyskatkokset" "kaytto"]
                    :valittu-analyysi nil
                    :nakymassa? false
                    :yhteyskatkos-data {:pvm [1 2 3 4 5]
                                        :nakyma-1 [nil 4 5 3 4]
                                        :nakyma-2 [2 3 4 1 nil]
                                        :nakyma-3 [1 nil 3 4 nil]
                                        :nakyma-4 [1 2 nil nil nil]}
                    :yhteyskatkos-pvm-data {}
                    :yhteyskatkos-palvelut-data {}
                    :yhteyskatkosryhma-pvm-data {}
                    :yhteyskatkosryhma-palvelut-data {}
                    :yhteyskatkos-jarjestykset ["pvm" "palvelut"]
                    :yhteyskatkos-arvot ["katkokset" "katkosryhmät"]
                    :valittu-yhteyskatkos-jarjestys nil
                    :valittu-yhteyskatkos-arvo "katkokset"
                    :aloitus-valmis false
                    :arvoa-vaihdetaan? false
                    :hakuasetukset-nakyvilla? false
                    :kaytossa {:min-katkokset 0
                               :naytettavat-ryhmat #{:tallenna :hae :urakan :muut}}
                    :hakuasetukset {:min-katkokset 0
                                    :naytettavat-ryhmat #{:tallenna :hae :urakka :muut}}}))

(defn jarjestele-yhteyskatkos-data
  "Muodostaa serveriltä haetusta yhteyskatkosdatasta semmoisen, että
   avaimina on ryhma-avain ja arvona ryhman katkokset."
  [jarjestys-avain ryhma-avain yhteyskatkos-data]
  (let [avainten-vaihto (map #(hash-map :jarjestys-avain (jarjestys-avain %) :ryhma-avain (ryhma-avain %) :arvo-avain (:katkokset %))
                             yhteyskatkos-data)
        ryhmasta-vektori (if (vector? (:ryhma-avain (first avainten-vaihto)))
                           avainten-vaihto
                           (map #(update % :ryhma-avain
                                           (fn [ryhma-arvo]
                                              (vec (repeat (count (:arvo-avain %)) ryhma-arvo))))
                                avainten-vaihto)) ; ({:jarjestys-avain []/"" :ryhma-avain [] :arvo-avain []} {:jarjestys-avain []/"" :ryhma-avain [] :arvo-avain []})
        jarjestyksesta-arvo  (if (empty? (filter #(vector? (:jarjestys-avain %)) ryhmasta-vektori))
                              ryhmasta-vektori
                              (mapcat #(map-indexed (fn [index vektori-elementti]
                                                       (let [arvo-paivitetty {:arvo-avain [(get (:arvo-avain %) index)]
                                                                              :ryhma-avain [(get (:ryhma-avain %) index)]
                                                                              :jarjestys-avain vektori-elementti}]
                                                        arvo-paivitetty))
                                                    (:jarjestys-avain %))
                                      ryhmasta-vektori)) ; ({:jarjestys-avain ["ping" "raportoi-yhteyskatkos"], :ryhma-avain ["2017-05-30" "2017-05-30"], :arvo-avain [7 2]}) -> ({:jarjestys-avain "ping" :ryhma-avain ["2017-05-30"] :arvo-avain [7]} {:jarjestys-avain "raportoi-yhteyskatkos" :ryhma-avain ["2017-05-30"] :arvo-avain [2]})

        poista-nillit-ryhmista (map (fn [mappi]
                                      (let [arvo-avaimet (vec (map-indexed #(if (nil? (get (:ryhma-avain mappi) %1))
                                                                                nil %2)
                                                                            (:arvo-avain mappi)))]
                                        (assoc mappi :arvo-avain (keep identity arvo-avaimet) :ryhma-avain (keep identity (:ryhma-avain mappi)))))
                                    jarjestyksesta-arvo)
        ota-nil-jarjestykset-pois (keep #(if (nil? (:jarjestys-avain %))
                                            nil %)
                                        poista-nillit-ryhmista)
        ota-tyhjat-ryhmat-pois (keep #(if (empty? (:ryhma-avain %))
                                        nil %)
                                      ota-nil-jarjestykset-pois)
        ota-tyhjat-arvot-pois (keep #(if (empty? (:arvo-avain %))
                                        nil %)
                                    ota-tyhjat-ryhmat-pois)]
    ota-tyhjat-arvot-pois))



(defn hae-yhteyskatkosten-data
  "Hakee serveriltä yhteyskatkosdatan."
   ([callback]
    (hae-yhteyskatkosten-data callback {}))
   ([callback hakuasetukset]
    (go (try (let [data (<! (k/post! :graylog-hae-yhteyskatkokset hakuasetukset))]
                (callback data))
             (catch :default e
                (js/console.log (pr-str e)))))))

(defrecord PaivitaArvo [arvo avain])
(defrecord Nakymassa? [nakymassa?])
(defrecord HaeArvotEnsin [])
(defrecord PaivitaAloitusArvot [data])
(defrecord PaivitaArvoFunktio [funktio avain])
(defrecord KaytaAsetuksia [])

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
  HaeArvotEnsin
  (process-event [_ app]
    (hae-yhteyskatkosten-data (tuck/send-async! ->PaivitaAloitusArvot))
    app)
  KaytaAsetuksia
  (process-event [_ app]
    (hae-yhteyskatkosten-data (tuck/send-async! ->PaivitaAloitusArvot) {:naytettavat-ryhmat (get-in app [:hakuasetukset :naytettavat-ryhmat]) :min-katkokset (get-in app [:hakuasetukset :min-katkokset])})
    app)
  PaivitaAloitusArvot
  (process-event [{data :data} app]
    (let [ryhmakatkos-data (map #(assoc % :katkokset (vec (repeat (count (:katkokset %)) 1))) data) ;Muuttaa kaikkien katkoksien arvot ykköseksi
          a (assoc app :yhteyskatkos-data data
                       :yhteyskatkos-pvm-data (jarjestele-yhteyskatkos-data :pvm :palvelut data)
                       :yhteyskatkos-palvelut-data (jarjestele-yhteyskatkos-data :palvelut :pvm data)
                       :yhteyskatkosryhma-pvm-data (jarjestele-yhteyskatkos-data :pvm :palvelut ryhmakatkos-data)
                       :yhteyskatkosryhma-palvelut-data (jarjestele-yhteyskatkos-data :palvelut :pvm ryhmakatkos-data))]
      (assoc a :aloitus-valmis true))))


(s/def ::arvo-avain (s/coll-of (s/and integer? pos?)))
(s/def ::ryhma-avain (s/coll-of string?))
(s/def ::jarjestys-avain string?)
(s/def ::jarjestetty-yhteyskatkos-data-itemi (s/and (s/keys :req-un [::arvo-avain ::ryhma-avain ::jarjestys-avain])
                                                    #(= (count (:arvo-avain %)) (count (:ryhma-avain %)))))
(s/def ::jarjestetty-yhteyskatkos-data (s/coll-of ::jarjestetty-yhteyskatkos-data-itemi :kind seq?))

(s/fdef jarjestele-yhteyskatkos-data
  :args (s/cat :jarjestys-avain keyword?
               :ryhma-avain keyword?
               :yhteyskatkos-data ::dgl/parsittu-yhteyskatkos-data)
  :ret ::jarjestetty-yhteyskatkos-data)
