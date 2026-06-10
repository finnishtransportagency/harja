(ns harja.views.urakka.muutokset.laskutusrajan-tarkistukset
  "Muutokset välilehden 'Laskutusrajan automaattiset tarkistukset' -osio"
  (:require [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))

(defn laskutusrajan-automaattiset-tarkistukset-grid [e! {:keys [laskutusrajan-tarkistukset] :as app}]
  [grid/grid
   {:tunniste :id
    :luokat ["kirjatut-muutokset-grid"]
    :tyhja "Ei muutostyötilauksia."
    :voi-lisata? false
    :voi-kumota? false
    :voi-poistaa? (constantly false)
    :voi-muokata? false
    :rivin-luokka (fn [arvo _]
                    (let [rivin-id (:id arvo)
                          viimeksi-klikattu-id (-> app :viimeksi-valittu :id)]
                      (when (= viimeksi-klikattu-id rivin-id) "viimeksi-valittu-tausta")))}

   ;; Taulukon kentät
   [{:otsikko "Pvm"
     :nimi :voimassa_alkaen
     :tyyppi :pvm
     :leveys 10}

    {:otsikko "Muutostyötilaukset yhteensä (€)"
     :nimi :yhteensa
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false false)
     :leveys 15
     :tasaa :oikea}

    {:otsikko "%-osuus hoitovuoden alun indeksikorjatusta tavoitehinnasta"
     :nimi :prosenttiosuus
     :tyyppi :numero
     :leveys 15
     :tasaa :oikea
     :fmt #(when % (str % " %"))}

    {:otsikko "Laskutusrajan tarkistus (€)"
     :nimi :laskutusrajan-tarkistus
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false true)
     :tasaa :oikea
     :leveys 15}

    {:otsikko "Laskutusraja (€)"
     :nimi :tarkistettu-laskutusraja
     :tyyppi :numero
     :fmt (partial fmt/euro-opt false false)
     :leveys 15
     :tasaa :oikea}]
   laskutusrajan-tarkistukset])

(defn laskutusrajan-tarkistukset [e! {:keys [laskutusrajan-tarkistukset budjettitavoitteet] :as app}]
  (let [laskutusraja_kaytossa? (:laskutusraja_kaytossa? budjettitavoitteet)]
    (when laskutusraja_kaytossa?
      [yhteiset/kehystetty-avattava-grid e! app
       {:taulukon-avain :laskutusrajan-tarkistukset
        :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :laskutusrajan-tarkistukset))
        :otsikko "Laskutusrajan automaattiset tarkistukset"
        :summa (some-> laskutusrajan-tarkistukset last :laskutusrajan-tarkistus)
        :toiminnot (fn [e! app]
                     (let [tavoitehinta (-> app :budjettitavoitteet :hoitovuoden-alun-indeksikorjattu-tavoitehinta)
                           kolme-prosenttia (when tavoitehinta (* 0.03 tavoitehinta))]
                       [:div.laskutusraja-info
                        [:p "Laskutusrajaa voidaan tarkistaa hoitovuoden aikana, mikäli tilaaja teettää muutostöitä ja kirjallisten
                        muutostyötilausten yhteismäärä kyseiselle hoitovuodelle on vähintään 3 % em. hoitovuoden alun indeksikorjatusta tavoitehinnasta."]
                        [:p "Harja laskee laskutusrajan tarkistukset automaattisesti. Laskennassa huomioidaan Kirjallisesti sovitut
                        muutokset -osioon tallennetut erillisrahoitetut muutostyöt sekä tavoitehintaa nostavat pysyvät muutokset."]
                        [:p "Hoitovuoden alun indeksikorjattu tavoitehinta: "
                         (fmt/euro-opt true false tavoitehinta)
                         ", josta 3% on "
                         (fmt/euro-opt true false kolme-prosenttia) "."]]))
        :taulukko
        (fn [e! app]
          [:<>
           [laskutusrajan-automaattiset-tarkistukset-grid e! app]])}])))
