(ns harja.tiedot.ilmoitukset
  (:require [reagent.core :refer [atom]]
            [harja.domain.ilmoitukset :refer [+ilmoitustyypit+ kuittaustyypit ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<! reaction-writable]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.ilmoituskuittaukset :as kuittausten-tiedot]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [tuck.core :as t])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))


;; Valinnat jotka riippuvat ulkoisista atomeista
(defonce valinnat
  (reaction
   {:hallintayksikko (:id @nav/valittu-hallintayksikko)
    :urakka          (:id @nav/valittu-urakka)
    :valitun-urakan-hoitokaudet @u/valitun-urakan-hoitokaudet
    :urakoitsija     (:id @nav/valittu-urakoitsija)
    :urakkatyyppi    (:arvo @nav/valittu-urakkatyyppi)
    :hoitokausi      @u/valittu-hoitokausi
    :aikavali        (or @u/valittu-hoitokausi [nil nil])}))

(def ^{:const true}
  kuittaustyyppi-filtterit [:kuittaamaton :vastaanotto :aloitus :lopetus])

(defonce ilmoitukset
  (atom {:ilmoitusnakymassa? false
         :valittu-ilmoitus nil
         :uusi-kuittaus-auki? false
         :ilmoitushaku nil ;; ilmoitushaun timeout
         :ilmoitukset nil ;; haetut ilmoitukset
         :valinnat {:tyypit +ilmoitustyypit+
                    :kuittaustyypit (into #{} kuittaustyyppi-filtterit)
                    :hakuehto ""
                    :selite [nil ""]
                    :vain-myohassa? false
                    :aloituskuittauksen-ajankohta :kaikki}
         :kuittaa-monta nil}))

(defn- jarjesta-ilmoitukset [tulos]
  (reverse (sort-by
            :ilmoitettu
            pvm/ennen?
            (mapv
             (fn [ilmo]
               (assoc ilmo :kuittaukset
                      (sort-by :kuitattu pvm/ennen? (:kuittaukset ilmo))))
             tulos))))

(defn- hae
  "Ajastaa uuden ilmoitushaun. Jos ilmoitushaku on jo ajastettu, se perutaan ja uusi ajastetaan."
  ([app] (hae app 300))
  ([{haku :ilmoitushaku :as app} timeout]
   ;; Jos seuraava haku ollaan laukaisemassa, peru se
   (when haku
     (.clearTimeout js/window haku))
   (assoc app
          :ilmoitushaku (.setTimeout js/window
                                     (t/send-async! v/->HaeIlmoitukset)
                                     timeout))))

;; Kaikki mitä UI voi ilmoitusnäkymässä tehdä, käsitellään täällä
(extend-protocol t/Event
  v/AsetaValinnat
  (process-event [{valinnat :valinnat} app]
    (hae
     (assoc app :valinnat valinnat)))

  v/YhdistaValinnat
  (process-event [{valinnat :valinnat :as e} app]
    (hae
     (update-in app [:valinnat] merge valinnat)))

  v/HaeIlmoitukset
  (process-event [_ {valinnat :valinnat :as app}]
    (let [tulos! (t/send-async! v/->IlmoitusHaku)]
      (go
        (tulos!
         (<! (k/post! :hae-ilmoitukset
                      (-> valinnat
                          ;; jos tyyppiä/tilaa ei valittu, ota kaikki
                          (update :tyypit
                                  #(if (empty? %) +ilmoitustyypit+ %))
                          (update :kuittaustyypit
                                  #(if (empty? %) (into #{} kuittaustyyppi-filtterit) %))))))))
    app)

  v/IlmoitusHaku
  (process-event [{tulokset :tulokset} {valittu :valittu-ilmoitus :as app}]
    (hae (assoc app
                ;; Uudet ilmoitukset
                :ilmoitukset (jarjesta-ilmoitukset tulokset)

                ;; Jos on valittuna ilmoitus joka ei ole haetuissa, perutaan valinta
                :valittu-ilmoitus (if (some #(= (:ilmoitusid valittu) %)
                                            (map :ilmoitusid tulokset))
                                    valittu
                                    nil))
         60000))

  v/ValitseIlmoitus
  (process-event [{ilm :ilmoitus} app]
    (assoc app :valittu-ilmoitus ilm))

  v/PoistaIlmoitusValinta
  (process-event [_ app]
    (assoc app :valittu-ilmoitus nil))

  ;; Valitun ilmoituksen uuden kuittauksen teko

  v/AvaaUusiKuittaus
  (process-event [_ app]
    (assoc-in app [:valittu-ilmoitus :uusi-kuittaus] {}))


  v/SuljeUusiKuittaus
  (process-event [_ app]
    (assoc-in app [:valittu-ilmoitus :uusi-kuittaus] nil))


  ;; Monen kuittaaminen
  v/AloitaMonenKuittaus
  (process-event [_ app]
    (assoc app :kuittaa-monta {:ilmoitukset #{}
                               :vapaateksti ""}))

  v/ValitseKuitattavaIlmoitus
  (process-event [{i :ilmoitus} app]
    (update-in app [:kuittaa-monta :ilmoitukset]
               (fn [ilmoitukset]
                 (if (ilmoitukset i)
                   (disj ilmoitukset i)
                   (conj ilmoitukset i)))))

  v/AsetaKuittausTiedot
  (process-event [{tiedot :tiedot} {:keys [valittu-ilmoitus kuittaa-monta] :as app}]
    (if valittu-ilmoitus
      (update-in app [:valittu-ilmoitus :uusi-kuittaus] merge tiedot)
      (update-in app [:kuittaa-monta] merge tiedot)))

  v/Kuittaa
  (process-event [_ {:keys [valittu-ilmoitus kuittaa-monta] :as app}]
    (let [kuittaus (if valittu-ilmoitus
                     (:uusi-kuittaus valittu-ilmoitus)
                     (dissoc kuittaa-monta :ilmoitukset))
          ilmoitukset (or (and valittu-ilmoitus [valittu-ilmoitus])
                          (:ilmoitukset kuittaa-monta))
          tulos! (t/send-async! v/->KuittaaVastaus)]
      (go
        (tulos! (<! (kuittausten-tiedot/laheta-kuittaukset! ilmoitukset kuittaus)))))
    (if valittu-ilmoitus
      (assoc-in app [:valittu-ilmoitus :uusi-kuittaus :tallennus-kaynnissa?] true)
      (assoc-in app [:kuittaa-monta :tallennus-kaynnissa?] true)))

  v/KuittaaVastaus
  (process-event [{v :vastaus} {:keys [valittu-ilmoitus kuittaa-monta] :as app}]
    (hae
     (if valittu-ilmoitus
       ;; FIXME: käsittele tloik virhe
       (assoc-in app [:valittu-ilmoitus :uusi-kuittaus] nil)
       (assoc app :kuittaa-monta nil))))

  v/PeruMonenKuittaus
  (process-event [_ app]
    (assoc app :kuittaa-monta nil)))



(defn lisaa-kuittaus-valitulle-ilmoitukselle [kuittaus]
  (let [nykyiset-kuittaukset (:kuittaukset (:valittu-ilmoitus ilmoitukset))]
    ;; FIXME: implement
    #_(swap! valittu-ilmoitus assoc :kuittaukset
           (sort-by :kuitattu pvm/ennen?
                    (conj nykyiset-kuittaukset kuittaus)))))


(defonce karttataso-ilmoitukset (atom false))

(defonce ilmoitukset-kartalla
  (reaction
   (let [{:keys [ilmoitukset valittu-ilmoitus]} @ilmoitukset]
     (when @karttataso-ilmoitukset
       (kartalla-esitettavaan-muotoon
        (map
         #(assoc % :tyyppi-kartalla (get % :ilmoitustyyppi))
         ilmoitukset)
        valittu-ilmoitus)))))

(comment
  (defn avaa-uusi-kuittaus! []
    (reset! uusi-kuittaus-auki? true))

  (defn sulje-uusi-kuittaus! []
    (reset! uusi-kuittaus-auki? false)))

;; FIXME: nämä menevät tuck process-event käsittelyn ohi
(defn avaa-ilmoitus! [ilmoitus]
  (swap! ilmoitukset assoc :valittu-ilmoitus ilmoitus))

(defn sulje-ilmoitus! []
  ;; FIXME: tämä menee tuck ohi
  (swap! ilmoitukset assoc :valittu-ilmoitus nil))
