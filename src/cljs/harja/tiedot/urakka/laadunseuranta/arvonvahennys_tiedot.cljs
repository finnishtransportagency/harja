(ns harja.tiedot.urakka.laadunseuranta.arvonvahennys-tiedot
  (:require [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]

            [harja.ui.viesti :as viesti]

            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]))

(defn uusi-arvonvahennys [mhu25? valittu-hoitokauden-alkuvuosi]
  (let [nyt (pvm/nyt)
        ;mhu25+ urakoille perintäpäivä on aina hoitovuoden viimeisessä kuussa. Muilla se on on kuluva valittu hoitovuosi ja kuluva kuukausi.
        muiden-vuosi (if (< (pvm/kuukausi nyt) 10) (inc valittu-hoitokauden-alkuvuosi) valittu-hoitokauden-alkuvuosi)
        default-perintapvm (if mhu25? ; mhu25 ja myöhempien urakoiden perintäpäivämäärä on aina defaulttina hoitokauden viimeisen kuukauden 15 päivä
                             (pvm/luo-pvm-dec-kk (inc valittu-hoitokauden-alkuvuosi) 9 15)
                             (pvm/luo-pvm-dec-kk muiden-vuosi (pvm/kuukausi nyt) 15))]
    {:suorasanktio true
     :laji :arvonvahennyssanktio
     :perintapvm default-perintapvm
     :maaraystapa :tyomaakokous
     :tehtavaryhma nil
     :tehtava nil
     :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                      :paatos {:paatos "sanktio"
                               :kasittelytapa :valikatselmus
                               :kasittelyaika nyt}}}))

(defrecord HaeKaikkiTehtavaryhmat [])
(defrecord HaeKaikkiTehtavaryhmatOnnistui [vastaus])
(defrecord HaeTehtavaryhmanTehtavat [id])
(defrecord HaeTehtavaryhmanTehtavatOnnistui [vastaus])
(defrecord KutsuEpaonnistui [viesti])

(extend-protocol tuck/Event

  HaeKaikkiTehtavaryhmat
  (process-event [_ app]
    (tuck-apurit/post! :hae-kaikkien-tehtavaryhmien-nimet
      {:urakka-id @nav/valittu-urakka-id}
      {:onnistui ->HaeKaikkiTehtavaryhmatOnnistui
       :epaonnistui ->KutsuEpaonnistui
       :epaonnistui-parametrit [{:viesti "Tehtäväryhmien haku epäonnistui"}]
       :paasta-virhe-lapi? true})
    (assoc app :haku-menossa true))

  HaeKaikkiTehtavaryhmatOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :tehtavaryhmat vastaus)
      (assoc :haku-menossa false)))

  HaeTehtavaryhmanTehtavat
  (process-event [{id :id} app]
    (tuck-apurit/post! :hae-tehtavaryhman-tehtavat-urakalle
      {:urakka-id @nav/valittu-urakka-id
       :tehtavaryhma-id id}
      {:onnistui ->HaeTehtavaryhmanTehtavatOnnistui
       :epaonnistui ->KutsuEpaonnistui
       :epaonnistui-parametrit [{:viesti "Tehtävien haku epäonnistui"}]
       :paasta-virhe-lapi? true})
    (assoc app :haku-menossa true))

  HaeTehtavaryhmanTehtavatOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :tehtavat vastaus)
      (assoc :haku-menossa false)))

  KutsuEpaonnistui
  (process-event [{{:keys [viesti]} :parametrit :as tulos} app]
    (cond
      ;; Jos backend heitti throw+, näytä käyttäjille selkeästi mikä virhe tapahtui
      (some? (get-in tulos [:tulos :response :virhe]))
      ;; Voi olla pitkä viesti, anna käyttäjälle aikaa lukea
      (viesti/nayta-toast! (str (get-in tulos [:tulos :response :virhe])) :varoitus viesti/viestin-nayttoaika-aareton)

      viesti
      (viesti/nayta! viesti :danger))

    (assoc app :haku-menossa false)))
