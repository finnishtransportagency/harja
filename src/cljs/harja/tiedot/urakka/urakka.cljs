(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom cursor]]
            [clojure.core.async :refer [chan]]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :as loki]
            [harja.pvm :as pvm]
            [clojure.string :as str]))

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

(defn ei-pakollinen [v-fn]
  (fn [arvo]
    (if-not (str/blank? arvo)
      (v-fn arvo)
      true)))

(defn ei-nil [arvo]
  (when
    (not (nil? arvo))
    arvo))

(defn ei-tyhja [arvo]
  (when
    (or
      (number? arvo)
      (pvm/pvm? arvo)
      (not (empty? arvo)))
    arvo))

(defn numero [arvo]
  (when
    (number? arvo)
    arvo))

(defn paivamaara [arvo]
  (when
    (pvm/pvm? arvo)
    arvo))

(defn y-tunnus [arvo]
  (when (re-matches #"\d{7}-\d" (str arvo)) arvo))

(defn viite [arvo]                                          ;11
  (let [tarkistusnumero (js/parseInt
                          (last arvo))                      ;1
        tarkastettavat-luvut (butlast arvo)]                ;1
    (loop [luvut (butlast tarkastettavat-luvut)             ;nil
           tarkasteltava (js/parseInt
                           (last tarkastettavat-luvut))     ;
           tarkistussumma 0
           paino 7]
      (if (nil? luvut)
        (let [lopullinen-summa (+ tarkistussumma
                                  (* tarkasteltava paino))
              vertailu-summa (+ lopullinen-summa
                                (- 10
                                   (let [viimeinen-numero (js/parseInt
                                                            (last
                                                              (str lopullinen-summa)))]
                                     (if (zero? viimeinen-numero)
                                       10
                                       viimeinen-numero))))]
          (when
            (= tarkistusnumero
               (- vertailu-summa
                  lopullinen-summa))
            arvo))
        (recur (butlast luvut)
               (js/parseInt
                 (last luvut))
               (+ tarkistussumma
                  (* tarkasteltava paino))
               (let [uusi-paino (/ (dec paino) 2)]
                 (if (zero? uusi-paino)
                   7
                   uusi-paino)))))))

(def validoinnit {:kulut/summa                 [ei-nil numero]
                  :kulut/viite                 [viite]
                  :kulut/laskun-numero         [(ei-pakollinen numero)]
                  :kulut/tehtavaryhma          [ei-nil ei-tyhja]
                  :kulut/aliurakoitsija        [ei-nil]
                  :kulut/erapaiva              [ei-nil ei-tyhja paivamaara]
                  :kulut/koontilaskun-kuukausi [ei-nil]})

(defn validoi-fn
  "Kutsuu vain lomakkeen kaikki validointifunktiot ja päivittää koko lomakkeen validiuden"
  [lomake]
  (if (nil? (meta lomake))
    lomake
    (vary-meta
      lomake
      (fn [{:keys [validius validi?] :as lomake-meta} lomake]
        (reduce (fn [kaikki [polku {:keys [validointi] :as validius}]]
                  (as-> kaikki kaikki
                        (update kaikki :validius
                                (fn [vs]
                                  (update vs polku
                                          (fn [kentta]
                                            (.log js/console "validoi kenttä " (pr-str kentta) ", polku " (pr-str polku) ", validointi: " (pr-str validointi))
                                            (assoc kentta
                                              :tarkistettu? true
                                              :validointi validointi
                                              :validi? (validointi
                                                         (get-in lomake polku)))))))
                        (update kaikki :validi?
                                (fn [v?]
                                  (not
                                    (some (fn [[avain {validi? :validi?}]]
                                            (false? validi?)) (:validius kaikki)))))))
                lomake-meta
                validius))
      lomake)))

(defn luo-validointi-fn
  "Yhdistää monta validointifunktiota yhdeksi"
  [validointi-fns]
  (fn [arvo]
    (let [validointi-fn (apply comp validointi-fns)]
      (not
        (nil?
          (validointi-fn arvo))))))

(defn luo-validius-meta
  "Ajatus, että lomake tietää itse, miten se validoidaan"
  [& kentat-ja-validaatiot]
  (assoc {} :validius
            (reduce (fn [k [polku validointi-fns]]
                      (assoc k polku {:validointi   (luo-validointi-fn validointi-fns)
                                      :validi?      false
                                      :koskettu?    false
                                      :tarkistettu? false}))
                    {}
                    (partition 2 kentat-ja-validaatiot))
            :validi? false
            :validoi validoi-fn))

(def kulun-oletus-validoinnit
  [[:koontilaskun-kuukausi] (:kulut/koontilaskun-kuukausi validoinnit)
   [:erapaiva] (:kulut/erapaiva validoinnit)
   [:laskun-numero] (:kulut/laskun-numero validoinnit)
   [:viite] (:kulut/viite validoinnit)
   [:aliurakoitsija] (:kulut/aliurakoitsija validoinnit)])

(defn kulun-validointi-meta [{:keys [kohdistukset] :as _kulu}]
  (apply luo-validius-meta
         (concat kulun-oletus-validoinnit
                 (mapcat (fn [i]
                           [[:kohdistukset i :summa] (:kulut/summa validoinnit)
                            [:kohdistukset i :tehtavaryhma] (:kulut/tehtavaryhma validoinnit)])
                         (range (count kohdistukset))))))

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
                                      :erapaiva              nil
                                      :paivita               0}
                                     (kulun-validointi-meta {:kohdistukset [{}]})))

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
