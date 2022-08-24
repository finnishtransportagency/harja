(ns harja.tiedot.ilmoitukset.tieliikenneilmoitukset
  (:require [reagent.core :refer [atom]]
            [harja.domain.tieliikenneilmoitukset :refer [+ilmoitustyypit+ kuittaustyypit ilmoitustyypin-nimi +ilmoitustilat+]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [harja.loki :refer [log tarkkaile!]]
            [alandipert.storage-atom :refer [local-storage]]
            [cljs.core.async :refer [<!]]
            [clojure.set :as set]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<! reaction-writable]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.ilmoituskuittaukset :as kuittausten-tiedot]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [tuck.core :as t]
            [harja.ui.viesti :as viesti]
            [clojure.string :as str]
            [reagent.core :as r])

  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def aikavalit [{:nimi "1 tunnin ajalta" :tunteja 1}
                {:nimi "12 tunnin ajalta" :tunteja 12}
                {:nimi "1 päivän ajalta" :tunteja 24}
                {:nimi "1 viikon ajalta" :tunteja 168}
                {:nimi "Vapaa aikaväli" :vapaa-aikavali true}])

(def toimenpiteiden-aikavalit
  [{:nimi "Ei rajoitusta" :ei-kaytossa? true}
   {:nimi "1 tunnin sisällä" :tunteja 1}
   {:nimi "12 tunnin sisällä" :tunteja 12}
   {:nimi "1 päivän sisällä" :tunteja 24}
   {:nimi "1 viikon sisällä" :tunteja 168}
   {:nimi "Vapaa aikaväli" :vapaa-aikavali true}])

;; Valinnat jotka riippuvat ulkoisista atomeista
(defonce valinnat
  (reaction
    {:voi-hakea? true
     :hallintayksikko (:id @nav/valittu-hallintayksikko)
     :urakka (:id @nav/valittu-urakka)
     :valitun-urakan-hoitokaudet @u/valitun-urakan-hoitokaudet
     :urakoitsija (:id @nav/valittu-urakoitsija)
     :urakkatyyppi (:arvo @nav/urakkatyyppi)
     :hoitokausi @u/valittu-hoitokausi}))


(def ^{:const true}
tila-filtterit [:kuittaamaton :vastaanotettu :aloitettu :lopetettu])

(def ^{:const true} vaikutukset-filtterit
  [:myohassa :aiheutti-toimenpiteita])

(def aanimerkki-uusista-ilmoituksista? (local-storage (atom true) :aanimerkki-ilmoituksista))

(defonce ilmoitukset
  (atom {:ilmoitusnakymassa?            false
         :edellinen-valittu-ilmoitus-id nil
         :valittu-ilmoitus              nil
         :uusi-kuittaus-auki?           false
         :ensimmainen-haku-tehty?       false
         :ilmoitushaku-id               nil ;; ilmoitushaun timeout
         :taustahaku?                   false ;; true jos haku tehdään taustapollauksena (ei käyttäjän syötteestä)
         :ilmoitukset                   nil ;; haetut ilmoitukset
         :valinnat                      {:tyypit                               +ilmoitustyypit+
                                         :tilat                                (into #{} tila-filtterit)
                                         :hakuehto                             ""
                                         :selite                               [nil ""]
                                         :vain-myohassa?                       false
                                         :aloituskuittauksen-ajankohta         :kaikki
                                         :ilmoittaja-nimi                      ""
                                         :ilmoittaja-puhelin                   ""
                                         :valitetty-urakkaan-vakioaikavali             (second aikavalit)
                                         :toimenpiteet-aloitettu-vakioaikavali (first toimenpiteiden-aikavalit)
                                         :valitetty-urakkaan-alkuaika                  (pvm/tuntia-sitten 12)
                                         :valitetty-urakkaan-loppuaika                 (pvm/nyt)}
         :kuittaa-monta                 nil}))

(defn- jarjesta-ilmoitukset [tulos]
  (reverse (sort-by
             :valitetty-urakkaan
             pvm/ennen?
             (mapv
               (fn [ilmo]
                 (assoc ilmo :kuittaukset
                             (sort-by :kuitattu pvm/ennen? (:kuittaukset ilmo))))
               tulos))))

(defn- merkitse-uudet-ilmoitukset [ilmoitukset uudet-ilmoitusidt]
  (map
    (fn [ilmoitus]
      (if (uudet-ilmoitusidt (:id ilmoitus))
        (with-meta ilmoitus {:uusi? true})
        ilmoitus))
    ilmoitukset))

(defn- nayta-notifikaatio-uusista-ilmoituksista [uudet-ilmoitukset optiot]
  (let [uusien-ilmoitusten-maara (count uudet-ilmoitukset)
        uusien-toimenpidepyyntojen-maara (count
                                           (filter #(= (:ilmoitustyyppi %) :toimenpidepyynto)
                                                   uudet-ilmoitukset))
        uusien-tiedoituksien-maara (count
                                     (filter #(= (:ilmoitustyyppi %) :tiedoitus)
                                             uudet-ilmoitukset))
        uusien-kyselyjen-maara (count
                                 (filter #(= (:ilmoitustyyppi %) :kysely)
                                         uudet-ilmoitukset))
        notifikaatio-body
        (fn [uusien-toimenpidepyyntojen-maara
             uusien-tiedoituksien-maara
             uusien-kyselyjen-maara]
          (str (when (> uusien-toimenpidepyyntojen-maara 0)
                 (if (= uusien-toimenpidepyyntojen-maara 1)
                   "1 uusi toimenpidepyyntö\n"
                   (str uusien-toimenpidepyyntojen-maara " uutta toimenpidepyyntöä\n")))
               (when (> uusien-tiedoituksien-maara 0)
                 (if (= uusien-tiedoituksien-maara 1)
                   "1 uusi tiedotus\n"
                   (str uusien-tiedoituksien-maara " uutta tiedotusta\n")))
               (when (> uusien-kyselyjen-maara 0)
                 (if (= uusien-kyselyjen-maara 1)
                   "1 uusi kysely\n"
                   (str uusien-kyselyjen-maara " uutta kyselyä\n")))))]
    (when (not (empty? uudet-ilmoitukset))
      (log "[ILMO] Uudet notifioitavat ilmoitukset: " (count uudet-ilmoitukset))
      (notifikaatiot/luo-notifikaatio
        (if (= uusien-ilmoitusten-maara 1)
          "Uusi ilmoitus Harjassa"
          (str uusien-ilmoitusten-maara " uutta ilmoitusta Harjassa"))
        (notifikaatio-body uusien-toimenpidepyyntojen-maara
                           uusien-tiedoituksien-maara
                           uusien-kyselyjen-maara)
        optiot))))

(def ^:const ilmoitushaun-viive-ms 3000)
(def ^:const taustahaun-viive-ms 60000)
(def ^:const ensimmaisen-haun-viive-ms 5000)

(defn- maarita-hakuviive [app]
  (if (:ensimmainen-haku-tehty? app)
    ilmoitushaun-viive-ms
    ensimmaisen-haun-viive-ms))

(defn- hae
  "Ajastaa uuden ilmoitushaun. Jos ilmoitushaku on jo ajastettu, se perutaan ja uusi ajastetaan.
   Viivettä käytetään, jotteivät useat peräkkäiset muutokset turhaan aiheuttaisi hakua palvelimelta."
  ([app] (hae app (maarita-hakuviive app)))
  ([app timeout] (hae app timeout false))
  ([{valinnat :valinnat haku :ilmoitushaku-id :as app} timeout taustahaku?]
   (if-not (:voi-hakea? valinnat)
     app
     (do
       ;; Jos seuraava haku ollaan laukaisemassa, peru se
       (when haku
         (.clearTimeout js/window haku))
       (-> app
           (assoc :ilmoitushaku-id (.setTimeout js/window
                                                (t/send-async! v/->HaeIlmoitukset)
                                                timeout))
           (assoc :taustahaku? taustahaku?)
           (assoc :ensimmainen-haku-tehty? true))))))

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
  (process-event [_ {valinnat :valinnat taustahaku? :taustahaku? :as app}]
    (let [tulos! (t/send-async! v/->IlmoitusHaku)]
      (go
        (let [haku (-> valinnat
                       ;; jos tyyppiä/tilaa ei valittu, ota kaikki
                       (update :tyypit
                               #(if (empty? %) +ilmoitustyypit+ %))
                       (update :tilat
                               #(if (empty? %) (into #{} tila-filtterit) %)))]
          (tulos!
            {:ilmoitukset (<! (k/post! :hae-ilmoitukset haku))
             :taustahaku? taustahaku?}))))
    (if taustahaku?
      app
      (assoc app :ilmoitukset nil)))

  v/IlmoitusHaku
  (process-event [{tulokset :tulokset} {valittu :valittu-ilmoitus :as app}]
    (let [uudet-ilmoitusidt (set/difference (into #{} (map :id (:ilmoitukset tulokset)))
                                            (into #{} (map :id (:ilmoitukset app))))
          uudet-ilmoitukset (filter #(uudet-ilmoitusidt (:id %)) (:ilmoitukset tulokset))]
      (when (:taustahaku? tulokset)
        (nayta-notifikaatio-uusista-ilmoituksista uudet-ilmoitukset
                                                  {:aani? @aanimerkki-uusista-ilmoituksista?}))
      (hae (assoc app
             ;; Uudet ilmoitukset
             :ilmoitukset (cond-> (:ilmoitukset tulokset)
                                  (:taustahaku? tulokset)
                                  (merkitse-uudet-ilmoitukset uudet-ilmoitusidt)
                                  true
                                  (jarjesta-ilmoitukset))

             ;; Jos on valittuna ilmoitus joka ei ole haetuissa, perutaan valinta
             :valittu-ilmoitus (if (some #(= (:ilmoitusid valittu) %)
                                         (map :ilmoitusid (:ilmoitukset tulokset)))
                                 valittu
                                 nil))
           taustahaun-viive-ms
           true)))

  v/ValitseIlmoitus
  (process-event [{ilmoitus :ilmoitus} app]
    (let [tulos (t/send-async! v/->IlmoituksenTiedot)]
      (go
        (tulos (<! (k/post! :hae-ilmoitus (:id ilmoitus))))))
    (assoc app :ilmoituksen-haku-kaynnissa? true))

  v/IlmoituksenTiedot
  (process-event [{ilmoitus :ilmoitus} app]
    (assoc app :valittu-ilmoitus ilmoitus
               :edellinen-valittu-ilmoitus-id (:id ilmoitus)
               :ilmoituksen-haku-kaynnissa? false))

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

  ;; Kuittaa joko monta tai valitun ilmoituksen kuittaus
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
    ;; Jos kuittaus onnistui, näytä viesti
    (when v
      (viesti/nayta! "Kuittaus lähetetty Tieliikennekeskukseen." :success))
    (hae
      (if valittu-ilmoitus
        (-> app
            (assoc-in [:valittu-ilmoitus :uusi-kuittaus] nil)
            (update-in [:valittu-ilmoitus :kuittaukset]
                       (fn [kuittaukset]
                         ;; Palvelin palauttaa vektorin kuittauksia, joihin
                         ;; olemassaolevat liitetään
                         (into (first v) kuittaukset))))
        (assoc app
          :kuittaa-monta nil
          :pikakuittaus nil))))

  v/PeruMonenKuittaus
  (process-event [_ app]
    (assoc app :kuittaa-monta nil))

  v/AloitaPikakuittaus
  (process-event [{:keys [ilmoitus kuittaustyyppi]} app]
    (assoc app :pikakuittaus
               {:ilmoitus ilmoitus
                :tyyppi kuittaustyyppi}))

  v/PaivitaPikakuittaus
  (process-event [{:keys [pikakuittaus]} app]
    (update app :pikakuittaus merge pikakuittaus))

  v/TallennaPikakuittaus
  (process-event [_ {:keys [pikakuittaus] :as app}]
    (let [tulos! (t/send-async! v/->KuittaaVastaus)]
      (go
        (tulos! (<! (kuittausten-tiedot/laheta-kuittaukset!
                      [(:ilmoitus pikakuittaus)]
                      (select-keys pikakuittaus #{:vapaateksti :vakiofraasi :tyyppi :aiheutti-toimenpiteita})))))
      (assoc-in app [:pikakuittaus :tallennus-kaynnissa?] true)))

  v/PeruutaPikakuittaus
  (process-event [_ app]
    (dissoc app :pikakuittaus))

  v/TallennaToimenpiteidenAloitus
  (process-event [{id :id} app]
    (let [tulos! (t/send-async! v/->ToimenpiteidenAloitusTallennettu)]
      (go
        (tulos! (<! (k/post! :tallenna-ilmoituksen-toimenpiteiden-aloitus [id]))))
      (assoc-in app [:toimenpiteiden-aloitus :tallennus-kaynnissa?] true)))

  v/PeruutaToimenpiteidenAloitus
  (process-event [{id :id} app]
    (let [tulos! (t/send-async! v/->ToimenpiteidenAloituksenPeruutusTallennettu)]
      (go
        (tulos! (<! (k/post! :peruuta-ilmoituksen-toimenpiteiden-aloitus [id]))))
      (assoc-in app [:toimenpiteiden-aloitus :tallennus-kaynnissa?] true)))

  v/ToimenpiteidenAloitusTallennettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden aloitus kirjattu" :success)
    ((t/send-async! v/->ValitseIlmoitus) (:valittu-ilmoitus app))
    (assoc-in app [:toimenpiteiden-aloitus :tallennus-kaynnissa?] false))

  v/ToimenpiteidenAloituksenPeruutusTallennettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden aloitus peruutettu" :success)
    ((t/send-async! v/->ValitseIlmoitus) (:valittu-ilmoitus app))
    (assoc-in app [:toimenpiteiden-aloitus :tallennus-kaynnissa?] false))

  v/TallennaToimenpiteidenAloitusMonelle
  (process-event [_ {:keys [kuittaa-monta] :as app}]
    (let [idt (map :id (:ilmoitukset kuittaa-monta))
          tulos! (t/send-async! v/->ToimenpiteidenAloitusMonelleTallennettu)]
      (go
        (tulos! (<! (k/post! :tallenna-ilmoituksen-toimenpiteiden-aloitus idt)))))
    (assoc-in app [:kuittaa-monta :tallennus-kaynnissa?] true))

  v/ToimenpiteidenAloitusMonelleTallennettu
  (process-event [{v :vastaus} app]
    (when v
      (viesti/nayta! "Toimenpiteiden aloitukset tallennettu." :success))
    (hae
      (assoc app
        :kuittaa-monta nil
        :pikakuittaus nil))))

(defonce karttataso-ilmoitukset (atom false))

(defonce ilmoitukset-kartalla
  (reaction
    (let [{:keys [ilmoitukset valittu-ilmoitus]} @ilmoitukset]
      (when @karttataso-ilmoitukset
        (kartalla-esitettavaan-muotoon
          (map
            #(assoc % :tyyppi-kartalla (get % :ilmoitustyyppi))
            ilmoitukset)
          #(= (:id %) (:id valittu-ilmoitus)))))))


(defn avaa-ilmoitus! [ilmoitus]
  ;; Tilannekuvan ilmoitustiedoissa on iso osa ilmoituksen tarkemmista tiedoista.
  ;; Laitetaan valituksi ilmoitukseksi se mitä ilmoituksesta tiedetään, ja täydennetään popupin tietoja
  ;; palvelinkutsulla, joka valmistuu jossain vaiheessa.
  (swap! ilmoitukset assoc :valittu-ilmoitus ilmoitus)
  (go
    (let [tulos (<! (k/post! :hae-ilmoitus (:id ilmoitus)))]
      (swap! ilmoitukset assoc :valittu-ilmoitus tulos))))

(defn sulje-ilmoitus! []
  (swap! ilmoitukset assoc :valittu-ilmoitus nil))

(def vihje-liito
  "Liidosta tuoduille ilmoituksille ei voi tehdä uusia kuittauksia")
