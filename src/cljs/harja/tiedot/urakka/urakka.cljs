(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:require [reagent.core :refer [atom cursor]]
            [clojure.core.async :refer [chan]]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.toteuma :as t]
            [harja.loki :as loki]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [reagent.core :as r]))

(defonce kustannussuunnitelma-default {:hankintakustannukset {:valinnat {:toimenpide                     :talvihoito
                                                                         :maksetaan                      :molemmat
                                                                         :kopioidaan-tuleville-vuosille? true
                                                                         :laskutukseen-perustuen-valinta #{}}}
                                       :suodattimet          {:hankinnat                      {:toimenpide                     :talvihoito
                                                                                               :maksetaan                      :molemmat
                                                                                               :kopioidaan-tuleville-vuosille? true
                                                                                               :laskutukseen-perustuen-valinta #{}}
                                                              :kopioidaan-tuleville-vuosille? true}})

(def suunnittelu-default-arvot {:tehtavat             {:valinnat {:samat-tuleville false
                                                                  :toimenpide      nil
                                                                  :valitaso        nil
                                                                  :noudetaan       0}}
                                :kustannussuunnitelma kustannussuunnitelma-default})

(defn silloin-kun [pred v-fn]
  (fn [arvo]
    (if (pred)
      (v-fn arvo)
      true)))

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
      (not (str/blank? arvo)))
    arvo))

(defn numero [arvo]
  (when
    (number? (-> arvo js/parseFloat))
    arvo))

(defn paivamaara [arvo]
  (when
    (pvm/pvm? arvo)
    arvo))

(defn y-tunnus [arvo]
  (when (re-matches #"\d{7}-\d" (str arvo)) arvo))

(def validoinnit {:kulut/summa                 [ei-nil numero]
                  :kulut/laskun-numero         [(ei-pakollinen ei-tyhja) (ei-pakollinen ei-nil)]
                  :kulut/tehtavaryhma          [ei-nil ei-tyhja]
                  :kulut/erapaiva              [ei-nil ei-tyhja paivamaara]
                  :kulut/koontilaskun-kuukausi [ei-nil]
                  :kulut/y-tunnus              [(ei-pakollinen y-tunnus)]
                  :kulut/lisatyon-lisatieto    [ei-nil ei-tyhja]
                  :kulut/toimenpideinstanssi   [ei-nil ei-tyhja]})
(defn validoi!
  [{:keys [validius validi?] :as lomake-meta} lomake]
  ;(loki/log "valids" validius)
  (reduce (fn [kaikki [polku {:keys [validointi] :as validius}]]
            (as-> kaikki kaikki
                  (update kaikki :validius
                          (fn [vs]
                            (update vs polku
                                    (fn [kentta]
                                      ; (.log js/console "validoi kenttä " (pr-str kentta) ", polku " (pr-str polku) ", validointi: " (pr-str validointi))
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

(defn validoi-fn
  "Kutsuu vain lomakkeen kaikki validointifunktiot ja päivittää koko lomakkeen validiuden"
  ([skeema lomake]
    (validoi! skeema lomake))
  ([lomake]
  (if (nil? (meta lomake))
    lomake
    (vary-meta
      lomake
      validoi!
      lomake))))

(defonce urakan-vaihto-triggerit (cljs.core/atom []))

(defn lisaa-urakan-vaihto-trigger!
  "Tämä funktio avulla voi lisätä funktion listaan, jonka kaikki funktiot ajetaan kun urakka vaihdetaan.
   Tässä voi siis tehdä jonkin tapaista siivousta."
  [f!]
  (swap! urakan-vaihto-triggerit conj f!))

(defn luo-validointi-fn
  "Yhdistää monta validointifunktiota yhdeksi"
  [validointi-fns]
  (fn [arvo]
    (let [validointi-fn (apply comp validointi-fns)]
      (not
        (nil?
          (validointi-fn arvo))))))

(defn luo-validius-tarkistukset
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
   [:laskun-numero] (:kulut/laskun-numero validoinnit)])

(defn kulun-validointi-meta
  ([kulu]
    (kulun-validointi-meta kulu {}))
  ([{:keys [kohdistukset] :as _kulu} opts]
  (apply luo-validius-tarkistukset
         (concat kulun-oletus-validoinnit
                 (mapcat (fn [i]
                           (if (= "lisatyo"
                                  (:maksueratyyppi
                                    (get kohdistukset i)))
                             [[:kohdistukset i :summa] (:kulut/summa validoinnit)
                              [:kohdistukset i :lisatyon-lisatieto] (:kulut/lisatyon-lisatieto validoinnit)
                              [:kohdistukset i :toimenpideinstanssi] (:kulut/toimenpideinstanssi validoinnit)]
                             [[:kohdistukset i :summa] (:kulut/summa validoinnit)
                              [:kohdistukset i :tehtavaryhma] (:kulut/tehtavaryhma validoinnit)]))
                         (range (count kohdistukset)))))))

(def kulut-kohdistus-default {:tehtavaryhma        nil
                              :toimenpideinstanssi nil
                              :summa               0
                              :poistettu           false
                              :lisatyo?            false
                              :rivi                0})

(def kulut-lomake-default (with-meta {:kohdistukset          [kulut-kohdistus-default]
                                      :aliurakoitsija        nil
                                      :koontilaskun-kuukausi nil
                                      :laskun-numero         nil
                                      :lisatieto             nil
                                      :suorittaja-nimi       nil
                                      :erapaiva              nil
                                      :liitteet              []
                                      :paivita               0}
                                     (kulun-validointi-meta {:kohdistukset [{}]})))

(def toteumat-default-arvot {:maarien-toteumat {:syottomoodi false
                                                :toimenpiteet nil
                                                :toteutuneet-maarat nil
                                                :valittu-toimenpide {:otsikko "Kaikki" :id 0}
                                                :hakufiltteri {:maaramitattavat true
                                                               :rahavaraukset true
                                                               :lisatyot true}
                                                :hoitokauden-alkuvuosi (if (>= (pvm/kuukausi (pvm/nyt)) 10)
                                                                         (pvm/vuosi (pvm/nyt))
                                                                         (dec (pvm/vuosi (pvm/nyt))))
                                                :aikavali-alkupvm nil
                                                :aikavali-loppupvm nil
                                                :lomake {::t/toimenpide nil
                                                         ::t/tyyppi nil
                                                         ::t/pvm (pvm/nyt)
                                                         ::t/toteumat [{::t/tehtava nil
                                                                        ::t/toteuma-id nil
                                                                        ::t/ei-sijaintia true
                                                                        ::t/toteuma-tehtava-id nil
                                                                        ::t/lisatieto nil
                                                                        ::t/maara nil}]}}})

(defonce toteumanakyma (atom toteumat-default-arvot))


(def kulut-default {:parametrit  {:haetaan 0}
                    :taulukko    nil
                    :lomake      kulut-lomake-default
                    :kulut       []
                    :syottomoodi false})

(def laskutus-default {:kohdistetut-kulut kulut-default})

(defonce tila (atom {:yleiset     {:urakka {}}
                     :laskutus    laskutus-default
                     :suunnittelu suunnittelu-default-arvot
                     :toteumat    toteumat-default-arvot}))


(defonce toteumat-maarat (cursor tila [:toteumat :maarien-toteumat]))

(defonce laskutus-kohdistetut-kulut (cursor tila [:laskutus :kohdistetut-kulut]))

(defonce yleiset (cursor tila [:yleiset]))

(defonce suunnittelu-tehtavat (cursor tila [:suunnittelu :tehtavat]))

(defonce suunnittelu-kustannussuunnitelma (cursor tila [:suunnittelu :kustannussuunnitelma]))

(defonce toteumat-maarien-toteumat (atom {:maarien-toteumat {:toimenpiteet          nil
                                                             :toteutuneet-maarat    nil
                                                             :hoitokauden-alkuvuosi (if (>= (pvm/kuukausi (pvm/nyt)) 10)
                                                                                      (pvm/vuosi (pvm/nyt))
                                                                                      (dec (pvm/vuosi (pvm/nyt))))
                                                             :aikavali-alkupvm      nil
                                                             :aikavali-loppupvm     nil
                                                             :toteuma               {:toimenpide         nil
                                                                                     :tehtava            nil
                                                                                     :toteuma-id         nil
                                                                                     :toteuma-tehtava-id nil
                                                                                     :lisatieto          nil
                                                                                     :maara              nil
                                                                                     :loppupvm           (pvm/nyt)}
                                                             :syottomoodi           false}}))

(add-watch nav/valittu-urakka :urakan-id-watch
           (fn [_ _ _ uusi-urakka]
             (doseq [f! @urakan-vaihto-triggerit]
               (f!))
             (when-not (= 0 (count @urakan-vaihto-triggerit))
               (reset! urakan-vaihto-triggerit []))
             (swap! tila (fn [tila]
                           (-> tila
                               (assoc-in [:yleiset :urakka] (dissoc uusi-urakka :alue))
                               (assoc :suunnittelu suunnittelu-default-arvot))))
             ;dereffataan kursorit, koska ne on laiskoja
             @suunnittelu-kustannussuunnitelma))
