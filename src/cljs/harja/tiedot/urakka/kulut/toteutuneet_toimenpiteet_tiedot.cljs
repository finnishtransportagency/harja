(ns harja.tiedot.urakka.kulut.toteutuneet-toimenpiteet-tiedot
  "Vesiväylä-hoitourakan toteutuneiden toimenpiteiden tila ja laskenta."
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]))

(defrecord HaeToteutuneetToimenpiteet [])
(defrecord ToteutuneetToimenpiteetHakuOnnistui [vastaus])
(defrecord ToteutuneetToimenpiteetHakuEpaonnistui [vastaus])
(defrecord ValitseRajauksenTyyppi [tyyppi])
(defrecord ValitseUrakkavuosi [vuosi])
(defrecord ValitseKuukausi [kuukausi])
(defrecord ValitseAikavali [aikavali])

(defn maaramuutos [tavoitemaara toteutunut-maara]
  (when (and (some? tavoitemaara) (some? toteutunut-maara))
    (- toteutunut-maara tavoitemaara)))

(defn maaramuutos-prosentteina [tavoitemaara maaramuutos]
  (when (and (some? tavoitemaara)
             (not (zero? tavoitemaara))
             (some? maaramuutos))
    (* 100 (/ maaramuutos tavoitemaara))))

(defn yksikkohinnan-keskiarvo [toteutunut-kustannus toteutunut-maara]
  (when (and (some? toteutunut-kustannus)
             (some? toteutunut-maara)
             (not (zero? toteutunut-maara)))
    (/ toteutunut-kustannus toteutunut-maara)))

(defn tavoitehintavaikutus [rivi koko-hoitokausi?]
  (let [{:keys [tavoitemaara toteutunut_maara toteutunut_kustannus
                tavoitehintavaikutus_aina tavoitehintavaikutus_ehdollinen]} rivi
        muutos (when koko-hoitokausi?
                 (maaramuutos tavoitemaara toteutunut_maara))
        muutos-prosentteina (when koko-hoitokausi?
                              (maaramuutos-prosentteina tavoitemaara muutos))
        yksikkohinta (yksikkohinnan-keskiarvo toteutunut_kustannus toteutunut_maara)
        vaikuttaa? (or tavoitehintavaikutus_aina
                       (and (some? tavoitehintavaikutus_ehdollinen)
                            (some? muutos-prosentteina)
                            (>= (js/Math.abs muutos-prosentteina)
                                tavoitehintavaikutus_ehdollinen)))]
    (when (and vaikuttaa? (some? muutos) (some? yksikkohinta))
      (* muutos yksikkohinta))))

(defn laske-rivi [rivi koko-hoitokausi?]
  (let [maara (:toteutunut_maara rivi)
        tavoite (:tavoitemaara rivi)
        muutos (when koko-hoitokausi?
                 (maaramuutos tavoite maara))]
    (assoc rivi
      :toteutunut_maara maara
      :yksikkohinnan_keskiarvo (yksikkohinnan-keskiarvo
                                (:toteutunut_kustannus rivi)
                                maara)
      :maaramuutos muutos
      :maaramuutos_prosentteina (when koko-hoitokausi?
                                  (maaramuutos-prosentteina tavoite muutos))
      :vaikutus_tavoitehintaan (tavoitehintavaikutus
                                 (assoc rivi :toteutunut_maara maara)
                                 koko-hoitokausi?))))

(defn- urakkavuoden-aikavali [vuosi]
  (pvm/vuodesta-hoitokausi vuosi))

(defn- hae-toteutuneet-toimenpiteet [app]
  (let [{:keys [urakka]} @tila/yleiset
        {:keys [rajauksen-tyyppi kuukausi aikavali]} app
        [alkupvm loppupvm] (if (= :valittu-aikavali rajauksen-tyyppi)
                             aikavali
                             (if (= "Kaikki" kuukausi)
                              (pvm/vuodesta-hoitokausi
                                (pvm/vuosi (first @urakka-tiedot/valittu-hoitokausi)))
                              kuukausi))]
    (when (and (:id urakka) alkupvm loppupvm)
      (tuck-apurit/post!
        :hae-vhu-toteutuneet-toimenpiteet
        {:urakka-id (:id urakka)
         :hoitokauden-alkuvuosi (pvm/vuosi (first @urakka-tiedot/valittu-hoitokausi))
         :alkupvm (pvm/iso8601 alkupvm)
         :loppupvm (pvm/iso8601 loppupvm)}
        {:onnistui ->ToteutuneetToimenpiteetHakuOnnistui
         :epaonnistui ->ToteutuneetToimenpiteetHakuEpaonnistui
         :paasta-virhe-lapi? true})
      true)))

(defn- hae-ja-paivita-tila [app]
  (if (hae-toteutuneet-toimenpiteet app)
    (assoc app :haku-kaynnissa? true)
    (assoc app :haku-kaynnissa? false :toimenpiteet nil)))

(extend-protocol tuck/Event
  HaeToteutuneetToimenpiteet
  (process-event [_ app]
    (hae-ja-paivita-tila app))

  ToteutuneetToimenpiteetHakuOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :toimenpiteet (mapv #(laske-rivi %
                                             (and (= :urakkavuoden-mukaan (:rajauksen-tyyppi app))
                                                  (= "Kaikki" (:kuukausi app))))
                           (:toimenpiteet vastaus)))
      (assoc :haku-kaynnissa? false)))

  ToteutuneetToimenpiteetHakuEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Toteutuneiden toimenpiteiden haku epäonnistui." :danger)
    (assoc app :haku-kaynnissa? false))

  ValitseRajauksenTyyppi
  (process-event [{:keys [tyyppi]} app]
    (hae-ja-paivita-tila (assoc app :rajauksen-tyyppi tyyppi)))

  ValitseUrakkavuosi
  (process-event [{:keys [vuosi]} app]
    (let [aikavali (urakkavuoden-aikavali vuosi)
          app (assoc app
                :urakkavuosi vuosi
                :kuukausi "Kaikki"
                :aikavali aikavali)]
      (hae-ja-paivita-tila app)))

  ValitseKuukausi
  (process-event [{:keys [kuukausi]} app]
    (hae-ja-paivita-tila (assoc app :kuukausi kuukausi)))

  ValitseAikavali
  (process-event [{:keys [aikavali]} app]
    (if (= aikavali (:aikavali app))
      app
      (hae-ja-paivita-tila (assoc app :aikavali aikavali)))))
