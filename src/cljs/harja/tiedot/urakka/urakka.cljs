(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom cursor]]
            [clojure.core.async :refer [chan]]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :as loki]))

(def suunnittelu-default-arvot {:tehtavat             {:valinnat {:samat-kaikille false
                                                                  :toimenpide     nil
                                                                  :valitaso       nil
                                                                  :noudetaan      0}}
                                :kustannussuunnitelma {:hankintakustannukset        {:valinnat {:toimenpide                     :talvihoito
                                                                                                :maksetaan                      :molemmat
                                                                                                :kopioidaan-tuleville-vuosille? true
                                                                                                :laskutukseen-perustuen-valinta #{}}}
                                                       :hallinnolliset-toimenpiteet {:valinnat {:maksetaan :molemmat}}
                                                       :kaskytyskanava              (chan)}})

(defn hankinnat-testidata [maara]
  (into []
        (drop 9
              (drop-last 3
                         (mapcat (fn [vuosi]
                                   (map #(identity
                                           {:pvm   (harja.pvm/luo-pvm vuosi % 15)
                                            :maara maara})
                                        (range 0 12)))
                                 (range (harja.pvm/vuosi (harja.pvm/nyt)) (+ (harja.pvm/vuosi (harja.pvm/nyt)) 6)))))))

(defn ei-nil [arvo] (not (nil? arvo)))
(defn ei-nil-ei-tyhja [arvo] (and
                        (ei-nil arvo)
                        (not (empty? arvo))))

(def validoinnit {:kulut/summa        ei-nil
                  :kulut/tehtavaryhma ei-nil
                  :kulut/erapaiva     ei-nil-ei-tyhja
                  :kulut/koontilaskun-kuukausi ei-nil})

(defn validoi-fn
  [lomake]
  (if (nil? (meta lomake))
    lomake
    (let [lomake (vary-meta
                   lomake
                   (fn [{:keys [validius validi?] :as lomake-meta} lomake]
                     (loki/log "methaa" lomake-meta)
                     (reduce (fn [kaikki [polku {:keys [validointi] :as validius}]]
                               (as-> kaikki kaikki
                                     (update
                                       kaikki
                                       :validius
                                       (fn [vs]
                                         (loki/log "vallut" vs "polku" polku)
                                         (update vs
                                                 polku (fn [kentta]
                                                         (loki/log "kentta" kentta "avain" polku "lomake" lomake "avainlomake" (get polku lomake))
                                                         (assoc kentta
                                                           :validointi validointi
                                                           :validi? (validointi
                                                                      (get-in lomake polku)))))))
                                     (update
                                       kaikki
                                       :validi?
                                       (fn [v?]
                                         (not
                                           (some (fn [[avain {validi? :validi?}]]
                                                   (false? validi?)) (:validius kaikki)))))))
                             lomake-meta
                             validius))
                   lomake)]
      lomake)))

(defn luo-validius-meta [& kentat-ja-validaatiot]
  (assoc {} :validius
            (reduce (fn [k [polku validointi-fn]]
                      (assoc k polku {:validointi validointi-fn
                                      :validi?    false
                                      :koskettu?  false}))
                    {}
                    (partition 2 kentat-ja-validaatiot))
            :validi? false
            :validoi validoi-fn))

(def kulut-lomake-default (with-meta {:kohdistukset          [{:tehtavaryhma        nil
                                                               :toimenpideinstanssi nil
                                                               :summa               nil
                                                               :rivi                0}]
                                      :aliurakoitsija        nil
                                      :koontilaskun-kuukausi nil
                                      :viite                 nil
                                      :laskun-numero         nil
                                      :lisatieto             nil
                                      :suorittaja-nimi       nil
                                      :erapaiva              nil}
                                     (luo-validius-meta
                                       [:koontilaskun-kuukausi] (:kulut/koontilaskun-kuukausi validoinnit)
                                       [:erapaiva] (:kulut/erapaiva validoinnit)
                                       [:kohdistukset 0 :summa] (:kulut/summa validoinnit)
                                       [:kohdistukset 0 :tehtavaryhma] (:kulut/tehtavaryhma validoinnit))))

(def kulut-default {:kohdistetut-kulut {:parametrit  {:haetaan 0}
                                        :taulukko    nil
                                        :lomake      kulut-lomake-default
                                        :kulut       []
                                        :syottomoodi false}})

(defonce tila (atom {:yleiset     {:urakka {}}
                     :laskutus    kulut-default
                     :suunnittelu suunnittelu-default-arvot}))


(defonce laskutus-kohdistetut-kulut (cursor tila [:laskutus :kohdistetut-kulut]))

(defonce yleiset (cursor tila [:yleiset]))

(defonce suunnittelu-tehtavat (cursor tila [:suunnittelu :tehtavat]))

(defonce suunnittelu-kustannussuunnitelma (cursor tila [:suunnittelu :kustannussuunnitelma]))

(add-watch nav/valittu-urakka :urakan-id-watch
           (fn [_ _ _ uusi-urakka]
             (swap! tila (fn [tila]
                           (-> tila
                               (assoc-in [:yleiset :urakka] (dissoc uusi-urakka :alue))
                               (assoc :suunnittelu suunnittelu-default-arvot))))))