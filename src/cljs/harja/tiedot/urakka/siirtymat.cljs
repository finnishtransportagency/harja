(ns harja.tiedot.urakka.siirtymat
  "Määrittelee siirtymiä, joilla pääsee suoraan linkittämään syvälle järjestelmään.
  Joissain näkymissä halutaan tarjota käyttäjälle kätevyyden nimissä nappi, jolla pääsee
  suoraan urakan tietoihin syvälle. Tämä vaatii useita eri tietoatomien manipulaatioita
  ja on hyvä keskittää yhteen paikkaan."
  (:require [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!] :as async]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- hae-toteuman-siirtymatiedot [toteuma-id]
  (k/post! :siirry-kokonaishintainen-toteuma toteuma-id))

(defn nayta-kokonaishintainen-toteuma!
  "Navigoi annetun urakan tietoihin ja näyttää kokonaishintaisen toteuman tiedot."
  [toteuma-id]
  (go
    (let [{:keys [aikavali tehtavat alkanut
                  urakka-id hallintayksikko-id
                  jarjestelmanlisaama] :as siirtyma}
          (<! (hae-toteuman-siirtymatiedot toteuma-id))
          {:keys [toimenpidekoodi toimenpideinstanssi]} (first tehtavat)]

      (log "SIIRTYMÄ: " (pr-str siirtyma))
      (nav/vaihda-kartan-koko! :L)

      ;; Valitse oikea toimenpideinstanssi
      (urakka/valitse-toimenpideinstanssi-koodilla! toimenpideinstanssi)

      ;; Valitaan sivuksi kok.hint. toteumat
      (nav/aseta-valittu-valilehti! :sivu :urakat)
      (nav/aseta-valittu-valilehti! :urakat :toteumat)
      (nav/aseta-valittu-valilehti! :toteumat :kokonaishintaiset-tyot)


      ;; Valitse toteuman urakka ja sen hallintayksikkö
      (nav/aseta-hallintayksikko-ja-urakka-id! hallintayksikko-id urakka-id)

      ;; Valitse aikaväliksi sama kuin tienäkymän valinnoissa
      (urakka/valitse-aikavali! (:alku aikavali) (:loppu aikavali))


      (let [pvm (pvm/paivan-alussa alkanut)
            tpk toimenpidekoodi
            jarj? jarjestelmanlisaama]
        ;; Aukaistaan vetolaatikko, joka sisältää tämän toteuman, valmiiksi
        (kokonaishintaiset-tyot/avaa-toteuma! urakka-id pvm tpk jarj?)

        ;; Valitaan päiväkohtainen tehtävä kartalle
        (kokonaishintaiset-tyot/valitse-paivakohtainen-tehtava! pvm tpk)

        ;; Valitaan yksittäinen toteuma katsottavaksi
        (kokonaishintaiset-tyot/valitse-paivan-toteuma-id! [urakka-id pvm tpk jarj?] toteuma-id)))))
