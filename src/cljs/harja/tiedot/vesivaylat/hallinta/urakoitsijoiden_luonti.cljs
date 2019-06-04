(ns harja.tiedot.vesivaylat.hallinta.urakoitsijoiden-luonti
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.domain.organisaatio :as o]
            [harja.domain.urakka :as u]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def uusi-urakoitsija {})

(defonce tila
  (atom {:nakymassa? false
         :valittu-urakoitsija nil
         :tallennus-kaynnissa? false
         :urakoitsijoiden-haku-kaynnissa? false
         :haetut-urakoitsijat nil
         :haetut-ytunnukset {}}))

(defn- aloitus [nyt [alku loppu]]
  (cond
    (pvm/valissa? nyt alku loppu) :kaynnissa
    (pvm/jalkeen? nyt loppu) :paattynyt
    (pvm/ennen? nyt alku) :alkava
    :else nil))

(defn urakoitsijan-urakat [urakoitsija]
  (let [urakat (::o/urakat urakoitsija)
        aloitus (partial aloitus (pvm/nyt))]
    (group-by (comp aloitus (juxt ::u/alkupvm ::u/loppupvm)) urakat)))

(defn urakoitsijan-urakoiden-lukumaarat-str [urakoitsija]
  (let [urakat (urakoitsijan-urakat urakoitsija)]
    (str (count (:alkava urakat)) " / "
         (count (:kaynnissa urakat)) " / "
         (count (:paattynyt urakat)))))

(defn urakan-aikavali-str [urakka]
  (->> urakka
       ((juxt ::u/alkupvm ::u/loppupvm))
       (map pvm/pvm)
       (str/join " - ")))

(defn- ytunnus-kaytossa? [tunnus valittu-urakoitsija tunnukset]
  (boolean
    (when-let [urakoitsija (tunnukset tunnus)]
     (not (= (::o/id urakoitsija) (::o/id valittu-urakoitsija))))))

(defn urakoitsijan-nimi-ytunnuksella [tunnus valittu-urakoitsija tunnukset]
  (when
    (ytunnus-kaytossa? tunnus valittu-urakoitsija tunnukset)
    (::o/nimi (tunnukset tunnus))))

(defn urakoitsijat-ytunnuksittain [urakoitsijat]
  (into {} (map (juxt ::o/ytunnus identity) urakoitsijat)))

(defrecord ValitseUrakoitsija [urakoitsija])
(defrecord Nakymassa? [nakymassa?])
(defrecord UusiUrakoitsija [])
(defrecord TallennaUrakoitsija [urakoitsija])
(defrecord UrakoitsijaTallennettu [urakoitsija])
(defrecord UrakoitsijaEiTallennettu [virhe])
(defrecord UrakoitsijaaMuokattu [urakoitsija])
(defrecord HaeUrakoitsijat [])
(defrecord UrakoitsijatHaettu [urakoitsijat])
(defrecord UrakoitsijatEiHaettu [virhe])
(defrecord HaeLomakevaihtoehdot [])
(defrecord LomakevaihtoehdotHaettu [tulos])
(defrecord LomakevaihtoehdotEiHaettu [virhe])

(extend-protocol tuck/Event
  ValitseUrakoitsija
  (process-event [{urakoitsija :urakoitsija} app]
    (assoc app :valittu-urakoitsija urakoitsija))

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  UusiUrakoitsija
  (process-event [_ app]
    (assoc app :valittu-urakoitsija uusi-urakoitsija))

  TallennaUrakoitsija
  (process-event [{urakoitsija :urakoitsija} app]
    (assert (some? (:haetut-urakoitsijat app)) "Urakoitsija ei voi yrittää tallentaa, ennen kuin urakoitsijoiden haku on valmis.")
    (let [tulos! (tuck/send-async! ->UrakoitsijaTallennettu)
          fail! (tuck/send-async! ->UrakoitsijaEiTallennettu)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :tallenna-urakoitsija urakoitsija))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :tallennus-kaynnissa? true))

  UrakoitsijaTallennettu
  (process-event [{urakoitsija :urakoitsija} app]
    (viesti/nayta! "Urakoitsija tallennettu!")
    (let [vanhat (group-by :id (:haetut-urakoitsijat app))
          uusi {(:id urakoitsija) [urakoitsija]}]
      ;; Yhdistetään tallennettu jo haettuihin.
      ;; Gridiin tultaessa Grid hakee vielä taustalla kaikki hankkeet
      ;; Tietokannasta asiat tulevat järjestettynä, mutta yritetään tässä jo saada oikea järjestys aikaan
      (assoc app :haetut-urakoitsijat (sort-by :nimi (vec (apply concat (vals (merge vanhat uusi)))))
                 :tallennus-kaynnissa? false
                 :valittu-urakoitsija nil)))

  UrakoitsijaEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Urakoitsijaa ei tallennettu."] :danger)
    (assoc app :tallennus-kaynnissa? false
               :valittu-urakoitsija nil))

  UrakoitsijaaMuokattu
  (process-event [{urakoitsija :urakoitsija} app]
    (assoc app :valittu-urakoitsija urakoitsija))

  HaeUrakoitsijat
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->UrakoitsijatHaettu)
          fail! (tuck/send-async! ->UrakoitsijatEiHaettu)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :vesivaylaurakoitsijat {}))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :urakoitsijoiden-haku-kaynnissa? true))

  UrakoitsijatHaettu
  (process-event [{urakoitsijat :urakoitsijat} app]
    (assoc app :haetut-urakoitsijat urakoitsijat
               :urakoitsijoiden-haku-kaynnissa? false))

  UrakoitsijatEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe urakoitsijoiden haussa!"] :danger)
    (assoc app :urakoitsijoiden-haku-kaynnissa? false))

  HaeLomakevaihtoehdot
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->LomakevaihtoehdotHaettu)
          fail! (tuck/send-async! ->LomakevaihtoehdotEiHaettu)]
      (go
        (try
          (let [urakoitsijat (async/<! (k/post! :hae-urakoitsijat-urakkatietoineen {}))
                ytunnukset (urakoitsijat-ytunnuksittain urakoitsijat)
                vastaus {:ytunnukset ytunnukset}]
            (if (some k/virhe? (vals vastaus))
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    app)

  LomakevaihtoehdotHaettu
  (process-event [{tulos :tulos} app]
    (harja.loki/log (pr-str (:ytunnukset tulos)))
    (assoc app :haetut-ytunnukset (:ytunnukset tulos)))

  LomakevaihtoehdotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Hupsista, ongelmia Harjan kanssa juttelussa." :danger)
    app))
