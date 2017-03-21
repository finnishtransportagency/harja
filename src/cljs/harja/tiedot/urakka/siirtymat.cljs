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
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- hae-toteuman-siirtymatiedot [toteuma-id]
  (k/post! :siirry-kokonaishintainen-toteuma toteuma-id))

(defn- hae-paallystysilmoituksen-tiedot [{:keys [paallystyskohde-id urakka-id]}]
  (k/post! :urakan-paallystysilmoitus-paallystyskohteella {:paallystyskohde-id paallystyskohde-id
                                                           :urakka-id urakka-id}))

(defn- odota-arvoa
  "Pollaa annettua atomia 100ms välein kunnes sen arvo on tosi arvo-pred mukaan.
  Lopettaa odotuksen, jos arvo ei täsmää timeout-ms kuluessa. Palauttaa kanavan,
  jonka arvo on true jos atomissa on odotettu arvo ja false jos odottaminen lopetettiin."
  [atomi arvo-pred timeout-ms]
  (let [lopeta-odotus (async/timeout timeout-ms)]
    (go
      (loop [[v ch] (alts! [lopeta-odotus (async/timeout 100)])]
        (if (= ch lopeta-odotus)
          false
          (if (arvo-pred @atomi)
            true
            (recur (alts! [lopeta-odotus (async/timeout 100)]))))))))

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
        (kokonaishintaiset-tyot/valitse-paivan-toteuma-id! [urakka-id pvm tpk jarj?] toteuma-id))

      ;; Tämä joudutaan tekemään valitettavasti odotuksella, koska toteumien
      ;; pääkomponentti asettaa sisään tullessa kartalle koon :S
      (go
        (when (<! (odota-arvoa kokonaishintaiset-tyot/nakymassa? true? 1000))
          (nav/vaihda-kartan-koko! :L))))))

(defn avaa-paallystysilmoitus!
  "Navigoi päällystysilmoitukseen näyttäen tiedot."
  [{:keys [paallystyskohde-id kohteen-urakka-id valittu-urakka-id] :as tiedot}]
  (go
    (let [{:keys [yllapitokohde-id urakka-id hallintayksikko-id] :as vastaus}
          (<! (hae-paallystysilmoituksen-tiedot {:paallystyskohde-id paallystyskohde-id
                                                 :urakka-id kohteen-urakka-id}))
          nykyinen-valilehti-taso1 @nav/valittu-sivu
          nykyinen-valilehti-taso2 (nav/valittu-valilehti :urakat)
          nykyinen-valilehti-taso3 (nav/valittu-valilehti :kohdeluettelo-paallystys)]

      ;; aseta urakka ja hy jos tarpeen
      (when-not (and valittu-urakka-id (= valittu-urakka-id kohteen-urakka-id urakka-id))
        (nav/aseta-hallintayksikko-ja-urakka-id! hallintayksikko-id urakka-id))

        ;; Vaihdetaan välilehtiä jos tarvetta
        (when-not (= nykyinen-valilehti-taso1 :urakat)
          (nav/aseta-valittu-valilehti! :sivu :urakat))

        (when-not (= nykyinen-valilehti-taso2 :kohdeluettelo-paallystys)
          (nav/aseta-valittu-valilehti! :urakat :kohdeluettelo-paallystys))

        (when-not (= nykyinen-valilehti-taso3 :paallystysilmoitukset)
          (nav/aseta-valittu-valilehti! :kohdeluettelo-paallystys :paallystysilmoitukset))

      (when (= paallystyskohde-id yllapitokohde-id)  ; estä pääsy toiseen ilmoitukseen esim. spoofaamalla ypk-id
        ;; Deeppi harppuuna: avataan päällystysilmoitus asettamalla päällystystieto ns:n atomiin data
        (reset! paallystys/paallystysilmoitus-lomakedata
                (assoc vastaus
                  :kirjoitusoikeus?
                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                            valittu-urakka-id)))))))
