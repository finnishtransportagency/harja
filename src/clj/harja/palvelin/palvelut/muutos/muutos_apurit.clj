(ns harja.palvelin.palvelut.muutos.muutos-apurit
  (:require [harja.kyselyt.konversio :as konv]))

(defn tavoitehinnan-muutos
  "Laskee rivin tavoitehinnan muutoksen. Sen sijainti vaihtelee tyyppikohtaisesti."
  ;; on hyvä saada tavoitehinnan muutos samaan avaimeen, niin summauslaskennat jne. toimivat myöhemmin suoraan
  [muutokset]
  (mapv
    (fn [rivi]
      (let [total (if (= (:tyyppi rivi)
                        "johto-ja-hallintokorvaus")
                    (or (:jjh-muutosten-summa rivi) 0)
                    (->>
                      (:kustannusvaikutukset rivi)
                      (map #(or (:summa %) 0))
                      (reduce + 0)))]
        (assoc rivi :tavoitehinnan-muutos total)))
    muutokset))

(defn parsi-kirjatut-muutokset-vastaus [vastaus]
  (->> vastaus
    (mapv (fn [rivi]
            (-> rivi
              (update :alityyppi #(keyword %))
              (update :kustannusvaikutukset #(konv/jsonb->clojuremap %))
              (update :tehtavat_ja_maarat #(konv/jsonb->clojuremap %))
              (update :liitteet #(konv/jsonb->clojuremap %)))))
    (tavoitehinnan-muutos)))

(defn laske-uusi-laskutusraja
  [paivitetaan? tyyppi-muutostyo? tyyppi-pysyva?
   muutosten-prosenttiosuus-tavoitehinnasta-uutta-luotaessa
   muutosten-prosenttiosuus-tavoitehinnasta-muokattaessa laskutusrajaa_nostettu?
   laskutusraja laskutusraja_alkuperainen
   hoitovuoden-tavoitehinta summa muutokset-yhteensa-kaikki
   muutokset-yhteensa-ilman-valittua aiempi-muutos]
  (let [;; Uuden muutoksen vaikutus laskutusrajaan:
        ;; muutostyö otetaan aina mukaan, pysyvä vain jos summa on positiivinen
        uuden-muutoksen-tyyppi-vaikuttaa-laskutusrajaan?
        (or tyyppi-muutostyo?
          (and tyyppi-pysyva? (pos? (or summa 0))))

        ;; Muokattavan muutoksen vaikutus laskutusrajaan:
        ;; olemassa oleva pysyvä/muutostyö voi joko nostaa rajaa tai palauttaa sen alkuperäiseksi
        muokattavan-muutoksen-tyyppi-vaikuttaa-laskutusrajaan?
        (or tyyppi-muutostyo? tyyppi-pysyva?)

        ;; Laskutusrajaa voidaan päivittää vain, jos nykyinen ja alkuperäinen raja sekä tavoitehinta löytyvät
        laskutusrajan-paivitys-mahdollinen?
        (and laskutusraja laskutusraja_alkuperainen hoitovuoden-tavoitehinta)

        ;; Uusi muutos nostaa laskutusrajaa, jos muutosten osuus on vähintään 3 %
        uusi-muutos-yli-rajan?
        (and (not paivitetaan?)
          uuden-muutoksen-tyyppi-vaikuttaa-laskutusrajaan?
          laskutusrajan-paivitys-mahdollinen?
          muutosten-prosenttiosuus-tavoitehinnasta-uutta-luotaessa
          (>= muutosten-prosenttiosuus-tavoitehinnasta-uutta-luotaessa 3.00))

        ;; Olemassa olevan muutoksen muokkaus nostaa tai korjaa laskutusrajaa, jos osuus on vähintään 3 %
        muokattu-muutos-yli-rajan?
        (and paivitetaan?
          muokattavan-muutoksen-tyyppi-vaikuttaa-laskutusrajaan?
          laskutusrajan-paivitys-mahdollinen?
          muutosten-prosenttiosuus-tavoitehinnasta-muokattaessa
          (>= muutosten-prosenttiosuus-tavoitehinnasta-muokattaessa 3.00))

        ;; Olemassa olevan muutoksen muokkaus palauttaa rajan alkuperäiseksi,
        ;; jos osuus tippuu alle 3 % ja rajaa oli jo aiemmin nostettu
        muokattu-muutos-alle-rajan?
        (and paivitetaan?
          muokattavan-muutoksen-tyyppi-vaikuttaa-laskutusrajaan?
          laskutusrajan-paivitys-mahdollinen?
          muutosten-prosenttiosuus-tavoitehinnasta-muokattaessa
          (< muutosten-prosenttiosuus-tavoitehinnasta-muokattaessa 3.00)
          laskutusrajaa_nostettu?)]

    (cond
      ;; Uutta muutosta luotaessa:
      ;; jos rajaa on jo nostettu, lisätään vain uusi summa
      ;; muuten lisätään uusi summa ja aiempi muutosten kertymä
      uusi-muutos-yli-rajan?
      (if laskutusrajaa_nostettu?
        (+ laskutusraja summa)
        (+ laskutusraja summa muutokset-yhteensa-kaikki))

      ;; Muutosta muokatessa:
      ;; jos rajaa on jo nostettu, vanha muutos vähennetään ja uusi summa lisätään
      ;; muuten lisätään uusi summa ja muiden muutosten kertymä
      muokattu-muutos-yli-rajan?
      (if laskutusrajaa_nostettu?
        (+ (- laskutusraja aiempi-muutos) summa)
        (+ laskutusraja summa muutokset-yhteensa-ilman-valittua))

      ;; Jos muokkauksen jälkeen osuus jää alle 3 %, palautetaan alkuperäinen laskutusraja
      muokattu-muutos-alle-rajan?
      laskutusraja_alkuperainen

      :else
      nil)))
