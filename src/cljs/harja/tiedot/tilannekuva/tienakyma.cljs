(ns harja.tiedot.tilannekuva.tienakyma
  "Tien 'supernäkymän' tiedot."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!] :as async]
            [tuck.core :as tuck]
            [harja.ui.kartta.infopaneelin-sisalto :as infopaneelin-sisalto]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [cljs-time.core :as t]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tienakyma (atom {:valinnat {}
                          :sijainti nil
                          :haku-kaynnissa? nil
                          :tulokset nil
                          :nakymassa? false
                          :reittipisteet {}
                          :naytetyt-toteumat #{}}))

(defrecord PaivitaSijainti [sijainti])
(defrecord PaivitaValinnat [valinnat])
(defrecord Hae [])
(defrecord HakuValmis [tulokset])
(defrecord Nakymassa [nakymassa?])
(defrecord SuljeInfopaneeli [])
(defrecord AvaaTaiSuljeTulos [idx])
(defrecord TarkasteleToteumaa [toteuma])
(defrecord HaeToteumanReittipisteet [toteuma])
(defrecord ToteumanReittipisteetHaettu [id reittipisteet])

(defn toteuman-reittipisteet-naytetty?
  ([toteuma] (toteuman-reittipisteet-naytetty? @tienakyma toteuma))
  ([app toteuma]
   (some? ((:naytetyt-toteumat app) (:id toteuma)))))

(defn- kartalle
  "Muodosta tuloksista karttataso.
  Kaikki, jotka ovat infopaneelissa avattuina, renderöidään valittuina."
  [{:keys [avatut-tulokset kaikki-tulokset valinnat reittipisteet] :as tienakyma}]
  (let [valittu? (comp boolean avatut-tulokset :idx)
        {valitut-tulokset true
         muut-tulokset false} (group-by valittu? kaikki-tulokset)]
    (assoc tienakyma
           :valitut-tulokset-kartalla
           (esitettavat-asiat/kartalla-esitettavaan-muotoon
            (concat (map #(if (contains? reittipisteet (:id %))
                            (assoc % :ei-nuolia? true)
                            %) valitut-tulokset)

                    ;; Lisätään reittipisteet toteumille, joille ne on haettu
                    (mapcat (fn [{id :id :as toteuma}]
                              (when-let [haetut-reittipisteet (reittipisteet id)]
                                (for [rp haetut-reittipisteet]
                                  (assoc rp
                                         :tehtavat (:tehtavat toteuma)
                                         :tyyppi-kartalla :reittipiste))))
                            (filter #(= (:tyyppi-kartalla %) :toteuma) valitut-tulokset))

                    [(assoc (:sijainti valinnat)
                            :tyyppi-kartalla :tr-osoite-indikaattori)])
            (constantly false))

           :muut-tulokset-kartalla
           (esitettavat-asiat/kartalla-esitettavaan-muotoon
            muut-tulokset
            (constantly false)))))

(extend-protocol tuck/Event
  Nakymassa
  (process-event [{nakymassa? :nakymassa?} {valinnat :valinnat :as tienakyma}]

    (as-> tienakyma tienakyma
      (assoc tienakyma :nakymassa? nakymassa?)

      (if (and nakymassa?
               (nil? (:alku valinnat))
               (nil? (:loppu valinnat)))
        ;; Näkymään tullaan eikä aikaa vielä ole asetettu, alustetaan myös päivämäärä ja kello
        (update tienakyma
                :valinnat assoc
                :alku (pvm/aikana (pvm/nyt) 0 0 0 0)
                :loppu (t/plus (pvm/nyt) (t/minutes 5)))
        tienakyma)))

  PaivitaSijainti
  (process-event [{s :sijainti} tienakyma]
    (assoc-in tienakyma [:valinnat :sijainti] s))

  PaivitaValinnat
  (process-event [{uusi :valinnat} tienakyma]
    (let [vanha (:valinnat tienakyma)
          alku-muuttunut? (not= (:alku vanha) (:alku uusi))
          tr-osoite-muuttunut? (not= (:tierekisteriosoite vanha) (:tierekisteriosoite uusi))
          valinnat (as-> uusi v
                     ;; Jos alku muuttunut ja vanhassa alku ja loppu olivat samat,
                     ;; päivitä myös loppukenttä
                     (if (and alku-muuttunut?
                              (= (:alku vanha) (:loppu vanha)))
                       (assoc v :loppu (:alku uusi))
                       v))]
      (as-> tienakyma tienakyma
        (assoc tienakyma
               :valinnat valinnat)
        (if tr-osoite-muuttunut?
          ;; Jos TR-osoite muuttuu, poistetaan tulokset
          (assoc tienakyma
                 :tulokset nil
                 :valitut-tulokset-kartalla nil
                 :muut-tulokset-kartalla nil)
          tienakyma))))

  Hae
  (process-event [_ tienakyma]
    (let [valinnat (:valinnat tienakyma)
          tulos! (tuck/send-async! ->HakuValmis)]
      (go
        (tulos! (<! (k/post! :hae-tienakymaan valinnat))))
      (assoc tienakyma
             :tulokset nil
             :tulokset-kartalla nil
             :haku-kaynnissa? true)))

  HakuValmis
  (process-event [{tulokset :tulokset} tienakyma]
    (let [{tulokset :tulos timeout? :timeout?} tulokset
          kaikki-tulokset (vec
                           (map-indexed (fn [i tulos]
                                          (assoc tulos :idx i))
                                        tulokset))]
      (log "Tienäkymän haku löysi " (count kaikki-tulokset) " tulosta")
      (when timeout?
        (viesti/nayta! "Kaikkia tuloksia ei keretty hakea! Uusi haku hetken kuluttua saattaa auttaa." :warning viesti/viestin-nayttoaika-keskipitka))
      (kartalle
       (assoc tienakyma
              :kaikki-tulokset kaikki-tulokset
              :tulokset (infopaneelin-sisalto/skeemamuodossa kaikki-tulokset)
              :avatut-tulokset #{}
              :haku-kaynnissa? false))))

  AvaaTaiSuljeTulos
  (process-event [{idx :idx} tienakyma]
    (kartalle
     (update tienakyma :avatut-tulokset
             #(if (% idx)
                (disj % idx)
                (conj % idx)))))

  SuljeInfopaneeli
  (process-event [_ tienakyma]
    (assoc tienakyma
           :tulokset nil
           :valitut-tulokset-kartalla nil
           :muut-tulokset-kartalla nil))

  TarkasteleToteumaa
  (process-event [{{id :id :as toteuma} :toteuma}
                  {{:keys [alku loppu]} :valinnat :as app}]
    (cond
      (= (:tyyppi-kartalla toteuma) :varustetoteuma)
      (siirtymat/nayta-varustetoteuma! id)

      :default
      (siirtymat/nayta-kokonaishintainen-toteuma! id))

    app)

  HaeToteumanReittipisteet
  (process-event [{toteuma :toteuma} app]
    (if ((:naytetyt-toteumat app) (:id toteuma))
      (kartalle (-> app
                    (update :naytetyt-toteumat disj (:id toteuma))
                    (update :reittipisteet dissoc (:id toteuma))))

      (do
        (let [tulos! (tuck/send-async! (partial ->ToteumanReittipisteetHaettu (:id toteuma)))]
          (go
            (tulos! (<! (k/post! :hae-reittipisteet-tienakymaan {:toteuma-id (:id toteuma)})))))
        app)))

  ToteumanReittipisteetHaettu
  (process-event [{:keys [id reittipisteet]} app]
    (log "Toteumalle " id " löytyi " (count reittipisteet) " reittipistettä.")
    (kartalle (-> app
                  (update :naytetyt-toteumat conj id)
                  (update :reittipisteet assoc id reittipisteet)))))

(defonce muut-tulokset-kartalla
  (r/cursor tienakyma [:muut-tulokset-kartalla]))

(defonce valitut-tulokset-kartalla
  (r/cursor tienakyma [:valitut-tulokset-kartalla]))

(defonce karttataso-tienakyma
  (r/cursor tienakyma [:nakymassa?]))
