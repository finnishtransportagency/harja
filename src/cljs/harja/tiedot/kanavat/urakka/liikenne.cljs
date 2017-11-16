(ns harja.tiedot.kanavat.urakka.liikenne
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tt]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [namespacefy.core :refer [namespacefy]]
            [harja.tiedot.urakka :as u]
            [reagent.core :as r]
            [harja.tiedot.istunto :as istunto]
            [clojure.string :as str]

            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [clojure.set :as set]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def tila (atom {:nakymassa? false
                 :liikennetapahtumien-haku-kaynnissa? false
                 :kohteiden-haku-kaynnissa? false
                 :tallennus-kaynnissa? false
                 :valittu-liikennetapahtuma nil
                 :tapahtumarivit nil
                 :urakan-kohteet nil
                 :valinnat {::ur/id nil
                            ::sop/id nil
                            :aikavali nil
                            ::lt/kohde nil
                            ::lt-alus/suunta nil
                            ::lt/toimenpide nil
                            ::lt-alus/laji nil
                            :niput? false}}))

(defn uusi-tapahtuma []
  {::lt/kuittaaja (namespacefy @istunto/kayttaja {:ns :harja.domain.kayttaja})
   ::lt/aika (pvm/nyt)
   ::lt/sopimus {::sop/id (first @u/valittu-sopimusnumero)
                 ::sop/nimi (second @u/valittu-sopimusnumero)}})

(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {::ur/id (:id @nav/valittu-urakka)
       :aikavali @u/valittu-aikavali
       ::sop/id (first @u/valittu-sopimusnumero)})))

(def valintojen-avaimet
  [::ur/id ::sop/id :aikavali ::lt/kohde ::lt-alus/suunta ::lt/toimenpide ::lt-alus/laji :niput?])

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeLiikennetapahtumat [])
(defrecord LiikennetapahtumatHaettu [tulos])
(defrecord LiikennetapahtumatEiHaettu [virhe])
(defrecord ValitseTapahtuma [tapahtuma])
(defrecord PaivitaValinnat [uudet])
(defrecord HaeKohteet [])
(defrecord KohteetHaettu [tulos])
(defrecord KohteetEiHaettu [virhe])
(defrecord TapahtumaaMuokattu [tapahtuma])
(defrecord MuokkaaAluksia [alukset])
(defrecord VaihdaSuuntaa [alus])
(defrecord TallennaLiikennetapahtuma [tapahtuma])
(defrecord TapahtumaTallennettu [tulos])
(defrecord TapahtumaEiTallennettu [virhe])

(defn valinta-wrap [e! app polku]
  (r/wrap (get-in app [:valinnat polku])
          (fn [u]
            (e! (->PaivitaValinnat {polku u})))))

(defn hakuparametrit [app]
  ;; Ei nil arvoja
  (when (and (::sop/id (:valinnat app))
             (::ur/id (:valinnat app)))
    (into {} (filter val (:valinnat app)))))

(defn- palvelumuoto->str* [pm lkm]
  (when pm
    (if (= :itse pm)
      (str (lt/palvelumuoto->str pm) " (" lkm " kpl)")
      (lt/palvelumuoto->str pm))))

(defn palvelumuoto->str [rivi]
  (let [sulku (palvelumuoto->str* (::lt/sulku-palvelumuoto rivi) (::lt/sulku-lkm rivi))
        silta (palvelumuoto->str* (::lt/silta-palvelumuoto rivi) (::lt/silta-lkm rivi))]
    (cond (and sulku silta)
          (str sulku " (sulku), " (str/lower-case silta) " (silta)")

          :else
          (or sulku silta))))

(defn toimenpide->str [rivi]
  (let [sulku (lt/sulku-toimenpide->str (::lt/sulku-toimenpide rivi))
        silta (when (::lt/silta-avaus rivi) "sillan avaus")]
    (str/capitalize (cond (and sulku silta)
                          (str sulku ", " silta)

                          :else
                          (or sulku silta)))))

(defn tapahtumarivit [tapahtuma]
  (let [yleistiedot
        (merge
          tapahtuma
          {:kohteen-nimi (str
                           (when-let [nimi (get-in tapahtuma [::lt/kohde ::kohde/kohteen-kanava ::kanava/nimi])]
                             (str nimi ", "))
                           (when-let [nimi (get-in tapahtuma [::lt/kohde ::kohde/nimi])]
                             (str nimi ", "))
                           (when-let [tyyppi (kohde/tyyppi->str (get-in tapahtuma [::lt/kohde ::kohde/tyyppi]))]
                             (str tyyppi)))})
        alustiedot
        (map
          (fn [alus]
            (merge
              yleistiedot
              alus
              {:suunta (::lt-alus/suunta alus)}))
          (::lt/alukset tapahtuma))]

    (if (empty? alustiedot)
      ;; Alustiedot ovat tyhjiä,
      ;; jos rivi on vain itsepalveluiden kirjaamista.
      [yleistiedot]
      alustiedot)))

(defn koko-tapahtuma [rivi {:keys [haetut-tapahtumat]}]
  (some #(when (= (::lt/id rivi) (::lt/id %)) %) haetut-tapahtumat))

(defn tapahtumat-haettu [app tulos]
  (-> app
      (assoc :liikennetapahtumien-haku-kaynnissa? false)
      (assoc :haetut-tapahtumat tulos)
      (assoc :tapahtumarivit (mapcat tapahtumarivit tulos))))

(defn voi-tallentaa? [t]
  true)

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeLiikennetapahtumat
  (process-event [_ app]
    (if-not (:liikennetapahtumien-haku-kaynnissa? app)
      (if-let [params (hakuparametrit app)]
        (-> app
            (tt/post! :hae-liikennetapahtumat
                      params
                      {:onnistui ->LiikennetapahtumatHaettu
                       :epaonnistui ->LiikennetapahtumatEiHaettu})
            (assoc :liikennetapahtumien-haku-kaynnissa? true))

        app)

      app))

  LiikennetapahtumatHaettu
  (process-event [{tulos :tulos} app]
    (tapahtumat-haettu app tulos))

  LiikennetapahtumatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Liikennetapahtumien haku epäonnistui! " :danger)
    (assoc app :liikennetapahtumien-haku-kaynnissa? false))

  ValitseTapahtuma
  (process-event [{t :tapahtuma} app]
    (assoc app :valittu-liikennetapahtuma (if (::lt/id t) (koko-tapahtuma t app) t)))

  PaivitaValinnat
  (process-event [{u :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys u valintojen-avaimet))
          haku (tuck/send-async! ->HaeLiikennetapahtumat)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  HaeKohteet
  (process-event [_ app]
    (if-not (:kohteiden-haku-kaynnissa? app)
      (-> app
          (tt/post! :hae-urakan-kohteet
                    {::ur/id (:id @nav/valittu-urakka)}
                    {:onnistui ->KohteetHaettu
                     :epaonnistui ->KohteetEiHaettu})
          (assoc :kohteiden-haku-kaynnissa? true))

      app))

  KohteetHaettu
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false)
        (assoc :urakan-kohteet tulos)))

  KohteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Virhe kohteiden haussa!" :danger)
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false)))

  TapahtumaaMuokattu
  (process-event [{t :tapahtuma} app]
    (assoc app :valittu-liikennetapahtuma t))

  MuokkaaAluksia
  (process-event [{alukset :alukset} app]
    (if (:valittu-liikennetapahtuma app)
      (assoc-in app [:valittu-liikennetapahtuma ::lt/alukset] alukset)

      app))

  VaihdaSuuntaa
  (process-event [{alus :alus} app]
    (let [alus (if (= :ylos (::lt-alus/suunta alus))
                 (assoc alus ::lt-alus/suunta :alas)
                 (assoc alus ::lt-alus/suunta :ylos))]
      (update app :valittu-liikennetapahtuma
              (fn [t]
                (update t ::lt/alukset
                        (fn [alukset]
                          (map #(if (= (::lt-alus/id %)
                                       (::lt-alus/id alus))
                                  alus
                                  %)
                               alukset)))))))

  TallennaLiikennetapahtuma
  (process-event [{t :tapahtuma} {:keys [tallennus-kaynnissa?] :as app}]
    (if-not tallennus-kaynnissa?
      (let [params (-> t
                       (assoc ::lt/kuittaaja-id (get-in t [::lt/kuittaaja ::kayttaja/id]))
                       (dissoc ::lt/kuittaaja)
                       (assoc ::lt/kohde-id (get-in t [::lt/kohde ::kohde/id]))
                       (dissoc ::lt/kohde)
                       (assoc ::lt/urakka-id (:id @nav/valittu-urakka))
                       (assoc ::lt/sopimus-id (get-in t [::lt/sopimus ::sop/id]))
                       (dissoc ::lt/sopimus)
                       (assoc :hakuparametrit (hakuparametrit app))
                       (update ::lt/alukset (fn [alukset] (map
                                                            #(set/rename-keys % {:poistettu ::m/poistettu?})
                                                            alukset))))]
        (-> app
            (tt/post! :tallenna-liikennetapahtuma
                      params
                      {:onnistui ->TapahtumaTallennettu
                       :epaonnistui ->TapahtumaEiTallennettu})
            (assoc :tallennus-kaynnissa? true)))

      app))

  TapahtumaTallennettu
  (process-event [{t :tulos} app]
    (when (modal/nakyvissa?) (modal/piilota!))
    (-> app
        (assoc :tallennus-kaynnissa? false)
        (assoc :valittu-liikennetapahtuma nil)
        (tapahtumat-haettu t)))

  TapahtumaEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Virhe tapahtuman tallennuksessa!" :danger)
    (assoc app :tallennus-kaynnissa? false)))
