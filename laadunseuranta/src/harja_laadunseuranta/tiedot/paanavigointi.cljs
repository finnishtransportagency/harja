(ns harja-laadunseuranta.tiedot.paanavigointi
  (:require [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.tiedot.nappaimisto :as nappaimisto]
            [cljs-time.local :as l]
            [harja-laadunseuranta.utils :as utils]
            [cljs-time.coerce :as tc]
            [cljs-time.local :as lt]))

;; Havainnot (ne, jotka esiintyvät usean välilehden alla, muutoin määritelty havainnot-ryhmittain -muuttujaan)

(def liikennemerkki-luminen
  {:nimi "Liikenne\u00ADmerkki lumi\u00ADnen"
   :ikoni "liikennemerkki-lumi-36"
   :ikoni-lahde "livicons"
   :tyyppi :piste
   :avain :liikennemerkki-luminen
   :vaatii-nappaimiston? false})

(def vesilammikot
  {:nimi "Vesi\u00ADlammi\u00ADkot"
   :tyyppi :vali
   :ikoni "vesilammikko-36"
   :ikoni-lahde "livicons"
   :avain :vesilammikot
   :vaatii-nappaimiston? false})

(def halkeamat
  {:nimi "Hal\u00ADkeamat"
   :tyyppi :vali
   :ikoni "halkeama-36"
   :ikoni-lahde "livicons"
   :avain :halkeamat
   :vaatii-nappaimiston? false})

;; Välilehdet ja niiden sisällöt

(def havainnot-ryhmittain
  ;; Jos muutat näitä, varmista, että tiedot ovat yhteneväiset kannassa olevien vakiohavaintojen kanssa
  ;; sekä palvelimen skeemassa. Erityisesti avain ja jatkuvuustieto on tärkeä kannassa.
  {:talvihoito [{:nimi "Liu\u00ADkas\u00ADta"
                 :ikoni "liukas-36"
                 :ikoni-lahde "livicons"
                 :tyyppi :vali
                 :avain :liukasta
                 :mittaus {:nimi "Liukkaus"
                           :tyyppi :kitkamittaus
                           :yksikko nil}
                 :vaatii-nappaimiston? true}
                {:nimi "Lu\u00ADmis\u00ADta"
                 :ikoni "lumi-36"
                 :ikoni-lahde "livicons"
                 :tyyppi :vali
                 :avain :lumista
                 :mittaus {:nimi "Lumisuus"
                           :tyyppi :lumisuus
                           :yksikko "cm"}
                 :vaatii-nappaimiston? true}
                {:nimi "Tasaus\u00ADpuute"
                 :avain :tasauspuute
                 :tyyppi :vali
                 :ikoni "epatasa-36"
                 :ikoni-lahde "livicons"
                 :mittaus {:nimi "Tasauspuute"
                           :tyyppi :talvihoito-tasaisuus
                           :yksikko "cm"}
                 :vaatii-nappaimiston? true}
                {:nimi "Pysäkki: epä\u00ADtas. polanne"
                 :tyyppi :piste
                 :avain :pysakilla-epatasainen-polanne
                 :ikoni "polanne-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}
                {:nimi "Pysäkki: auraa\u00ADmatta"
                 :tyyppi :piste
                 :avain :pysakki-auraamatta
                 :ikoni "pinta-lumi-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}
                {:nimi "Yli-/aliauraus"
                 :ikoni "auraus-36"
                 :ikoni-lahde "livicons"
                 :tyyppi :vali
                 :avain :yli-tai-aliauraus
                 :vaatii-nappaimiston? false}
                {:nimi "Pysäkki: hiekoit\u00ADtamatta"
                 :tyyppi :piste
                 :avain :pysakki-hiekoittamatta
                 :ikoni "pinta-hiekka-kielto-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}
                liikennemerkki-luminen]
   :liikennemerkit [{:nimi "Liikenne\u00ADmerkki likai\u00ADnen"
                     :ikoni "liikennemerkki-lika-36"
                     :ikoni-lahde "livicons"
                     :tyyppi :piste
                     :avain :liikennemerkki-likainen
                     :vaatii-nappaimiston? false}
                    {:nimi "Liikenne\u00ADmerkki vino\u00ADssa"
                     :ikoni "liikennemerkki-vino-36"
                     :ikoni-lahde "livicons"
                     :tyyppi :piste
                     :avain :liikennemerkki-vinossa
                     :vaatii-nappaimiston? false}
                    {:nimi "Liikenne\u00ADmerkki vauri\u00ADoitunut"
                     :ikoni "liikennemerkki-rikki-36"
                     :ikoni-lahde "livicons"
                     :tyyppi :piste
                     :avain :liikennemerkki-vaurioitunut
                     :vaatii-nappaimiston? false}
                    liikennemerkki-luminen]
   :viherhoito [{:nimi "Vesakko raivaa\u00ADmatta"
                 :tyyppi :vali
                 :ikoni "vesakko-leikkaus-36"
                 :ikoni-lahde "livicons"
                 :avain :vesakko-raivaamatta
                 :vaatii-nappaimiston? false}
                {:nimi "Niit\u00ADtämättä"
                 :tyyppi :vali
                 :avain :niittamatta
                 :ikoni "heina-leikkaus-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}
                {:nimi "Näkemä\u00ADalue raivaa\u00ADmatta"
                 :tyyppi :piste
                 :avain :nakemaalue-raivaamatta
                 :ikoni "heina-silma-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}
                {:nimi "Liiken\u00ADnetila hoita\u00ADmatta"
                 :tyyppi :piste
                 :avain :liikennetila-hoitamatta
                 :ikoni "tie-rikki-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}
                {:nimi "Istu\u00ADtukset hoita\u00ADmatta"
                 :tyyppi :piste
                 :avain :istutukset-hoitamatta
                 :ikoni "kukka-kuihtunut-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}
                {:nimi "P- tai L-alueet hoitamatta"
                 :tyyppi :piste
                 :avain :pl-alue-hoitamatta
                 :ikoni "pysakointi-rikki-36"
                 :ikoni-lahde "livicons"
                 :vaatii-nappaimiston? false}]
   :reunat [{:nimi "Reuna\u00ADpaalu likai\u00ADnen"
             :tyyppi :piste
             :ikoni "reunapaalu-lika-36"
             :ikoni-lahde "livicons"
             :avain :reunapaalut-likaisia
             :vaatii-nappaimiston? false}
            {:nimi "Reuna\u00ADpaalu vino\u00ADssa"
             :tyyppi :piste
             :avain :reunapaalut-vinossa
             :ikoni "reunapaalu-vino-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Reuna\u00ADpaalu vauri\u00ADoitunut"
             :tyyppi :piste
             :avain :reunapaalut-vaurioitunut
             :ikoni "reunapaalu-rikki-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Rumpu tukossa"
             :tyyppi :piste
             :avain :rumpu-tukossa
             :ikoni "rumpu-tukossa-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Oja tukossa"
             :tyyppi :piste
             :avain :oja-tukossa
             :ikoni "oja-tukossa-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Kaide\u00ADvaurio"
             :tyyppi :piste
             :avain :kaidevaurio
             :ikoni "kaidevaurio-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Kiveys\u00ADvaurio"
             :tyyppi :piste
             :avain :kiveysvaurio
             :ikoni "kiveysvaurio-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Reuna\u00ADpalletta"
             :tyyppi :vali
             :avain :reunapalletta
             :ikoni "reunapalle-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Reuna\u00ADtäyttö puutteel\u00ADlinen"
             :tyyppi :vali
             :avain :reunataytto-puutteellinen
             :vaatii-nappaimiston? false
             :ikoni "reunapuute-36"
             :ikoni-lahde "livicons"}]
   :p-ja-l-alueet [{:nimi "Auraa\u00ADmatta"
                    :tyyppi :piste
                    :avain :pl-alue-auraamatta
                    :ikoni "pinta-lumi-36"
                    :ikoni-lahde "livicons"
                    :vaatii-nappaimiston? false}
                   {:nimi "Hiekoit\u00ADtamatta"
                    :tyyppi :piste
                    :avain :pl-alue-hiekoittamatta
                    :ikoni "pinta-hiekka-kielto-36"
                    :ikoni-lahde "livicons"
                    :vaatii-nappaimiston? false}
                   {:nimi "Epä\u00ADtasainen polanne"
                    :tyyppi :piste
                    :avain :pl-epatasainen-polanne
                    :ikoni "polanne-36"
                    :ikoni-lahde "livicons"
                    :vaatii-nappaimiston? false}
                   {:nimi "Puhdis\u00ADtet\u00ADtava"
                    :tyyppi :piste
                    :ikoni "harja-36"
                    :ikoni-lahde "livicons"
                    :avain :pl-alue-puhdistettava
                    :vaatii-nappaimiston? false}
                   {:nimi "Korjat\u00ADtavaa"
                    :tyyppi :piste
                    :ikoni "jakoavain-36"
                    :ikoni-lahde "livicons"
                    :avain :pl-alue-korjattavaa
                    :vaatii-nappaimiston? false}]
   :sillat [{:nimi "Puhdista\u00ADmatta"
             :ikoni "harja-36"
             :ikoni-lahde "livicons"
             :tyyppi :piste
             :avain :silta-puhdistamatta
             :vaatii-nappaimiston? false}
            {:nimi "Sau\u00ADmoissa puut\u00ADteita"
             :tyyppi :piste
             :avain :siltasaumoissa-puutteita
             :ikoni "saumavirhe-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Pääl\u00ADlys\u00ADtees\u00ADsä vaurioita"
             :tyyppi :piste
             :avain :sillan-paallysteessa-vaurioita
             :vaatii-nappaimiston? false
             :ikoni "halkeama-36"
             :ikoni-lahde "livicons"}
            {:nimi "Kaide\u00ADvauri\u00ADoita"
             :tyyppi :piste
             :avain :sillassa-kaidevaurioita
             :ikoni "kaidevaurio-36"
             :ikoni-lahde "livicons"
             :vaatii-nappaimiston? false}
            {:nimi "Reuna\u00ADpalkki\u00ADvauri\u00ADoita"
             :tyyppi :piste
             :avain :sillassa-reunapalkkivaurioita
             :vaatii-nappaimiston? false
             :ikoni "kaidevaurio-36"
             :ikoni-lahde "livicons"}]
   :soratiet [{:nimi "Sora\u00ADtie\u00ADtarkas\u00ADtus"
               :tyyppi :vali
               :ikoni "suurennuslasi-36"
               :ikoni-lahde "livicons"
               :avain :soratie
               :mittaus {:nimi "Soratietarkastus"
                         :tyyppi :soratie
                         :yksikko nil}
               :vaatii-nappaimiston? true}
              {:nimi "Maa\u00ADkivi"
               :tyyppi :piste
               :ikoni "maakivi-36"
               :ikoni-lahde "livicons"
               :avain :maakivi
               :vaatii-nappaimiston? false}

              {:nimi "Kevät\u00ADmuokkaus\u00ADpuute"
               :tyyppi :vali
               :ikoni "harva-pinta-36"
               :ikoni-lahde "livicons"
               :avain :kevatmuokkauspuute
               :vaatii-nappaimiston? false}
              {:nimi "Sorastus\u00ADpuute"
               :tyyppi :vali
               :ikoni "pinta-hiekka-kielto-36"
               :ikoni-lahde "livicons"
               :avain :sorastuspuute
               :vaatii-nappaimiston? false}
              {:nimi "Keli\u00ADrikko\u00ADhavainnot"
               :tyyppi :vali
               :ikoni "kelirikko-36"
               :ikoni-lahde "livicons"
               :avain :kelirikkohavainnot
               :vaatii-nappaimiston? false}]
   :paallystyksen-tyovirheluettelo
   [{:nimi "Sauma\u00ADvirhe"
     :tyyppi :vali
     :ikoni "saumavirhe-36"
     :ikoni-lahde "livicons"
     :avain :saumavirhe
     :vaatii-nappaimiston? false}
    {:nimi "Lajit\u00ADtuma"
     :tyyppi :vali
     :ikoni "lajittuma-36"
     :ikoni-lahde "livicons"
     :avain :lajittuma
     :vaatii-nappaimiston? false}
    {:nimi "Epä\u00ADtasai\u00ADsuus"
     :tyyppi :vali
     :ikoni "epatasa-36"
     :ikoni-lahde "livicons"
     :avain :epatasaisuus
     :vaatii-nappaimiston? false}
    halkeamat
    vesilammikot
    {:nimi "Epä\u00ADtasai\u00ADset reunat"
     :tyyppi :vali
     :ikoni "epatasaiset-reunat-36"
     :ikoni-lahde "livicons"
     :avain :epatasaisetreunat
     :vaatii-nappaimiston? false}
    {:nimi "Jyrän jälkiä"
     :tyyppi :vali
     :ikoni "jyran-jalki-36"
     :ikoni-lahde "livicons"
     :avain :jyranjalkia
     :vaatii-nappaimiston? false}
    {:nimi "Side\u00ADaine\u00ADläikkiä"
     :tyyppi :vali
     :ikoni "laikka-36"
     :ikoni-lahde "livicons"
     :avain :sideainelaikkia
     :vaatii-nappaimiston? false}
    {:nimi "Vää\u00ADrä korkeus\u00ADasema"
     :tyyppi :vali
     :ikoni "vaara-korkeus-36"
     :ikoni-lahde "livicons"
     :avain :vaarakorkeusasema
     :vaatii-nappaimiston? false}
    {:nimi "Pinta harva"
     :tyyppi :vali
     :ikoni "harva-pinta-36"
     :ikoni-lahde "livicons"
     :avain :pintaharva
     :vaatii-nappaimiston? false}
    {:nimi "Pinta\u00ADkuivatus puut\u00ADteel\u00ADlinen"
     :tyyppi :vali
     :ikoni "pintakuivatus-puute-36"
     :ikoni-lahde "livicons"
     :avain :pintakuivatuspuute
     :vaatii-nappaimiston? false}
    {:nimi "Kai\u00ADvojen korkeus\u00ADasema"
     :tyyppi :vali
     :ikoni "kaivon-korkeus-36"
     :ikoni-lahde "livicons"
     :avain :kaivojenkorkeusasema
     :vaatii-nappaimiston? false}]
   :paallysteen-kunto
   [{:nimi "Yksit\u00ADtäinen reikä"
     :tyyppi :piste
     :ikoni "reika-36"
     :ikoni-lahde "livicons"
     :avain :yksittainen-reika
     :vaatii-nappaimiston? false}
    {:nimi "Reikä\u00ADjono"
     :tyyppi :vali
     :ikoni "reikajono-36"
     :ikoni-lahde "livicons"
     :avain :reikajono
     :vaatii-nappaimiston? false}
    halkeamat
    {:nimi "Reuna\u00ADpainuma"
     :tyyppi :vali
     :ikoni "epatasaiset-reunat-36"
     :ikoni-lahde "livicons"
     :avain :reunapainuma
     :vaatii-nappaimiston? false}
    {:nimi "Purkaumat"
     :tyyppi :vali
     :ikoni "kohouma-36"
     :ikoni-lahde "livicons"
     :avain :purkaumat
     :vaatii-nappaimiston? false}
    {:nimi "Syvät ajourat"
     :tyyppi :vali
     :ikoni "urat-36"
     :ikoni-lahde "livicons"
     :avain :syvat-ajourat
     :vaatii-nappaimiston? false}
    {:nimi "Turvallisuutta vaarantava heitto"
     :tyyppi :vali
     :ikoni "kohouma-vaara-36"
     :ikoni-lahde "livicons"
     :avain :liikenneturvallisuutta-vaarantava-heitto
     :vaatii-nappaimiston? false
     :pienempi-fontti? true}
    {:nimi "Ajomukavuutta haittaava epätas."
     :tyyppi :vali
     :ikoni "epatasa-36"
     :ikoni-lahde "livicons"
     :avain :ajomukavuutta-haittaava-epatasaisuus
     :vaatii-nappaimiston? false
     :pienempi-fontti? true}
    vesilammikot
    {:nimi "Harjaus\u00ADpuute"
     :tyyppi :vali
     :ikoni "harja-36"
     :ikoni-lahde "livicons"
     :avain :harjauspuute
     :vaatii-nappaimiston? false}]})

(defn jarjesta-valilehdet [valilehdet]
  (into [] (sort-by :jarjestys valilehdet)))

(defn- kayttajaroolin-valilehdet-paallystyksen-kunto [valilehdet oikeus-hoitourakkaan?]
  ;; Päällystyksen kunto -välilehti näytetään vain jos on oikeus johonkin hoitourakkaan
  (if oikeus-hoitourakkaan?
    valilehdet
    (filterv #(not= (:avain %) :paallystyksen-kunto) valilehdet)))

(defn- kayttajaroolin-valilehdet-paallystyksen-tyovirheluettelo [valilehdet oikeus-paallystysurakkaan?]
  ;; Päällystyksen työvirheluettelo näytetään vain jos on oikeus johonkin päällystysurakkaan
  (if oikeus-paallystysurakkaan?
    valilehdet
    (filterv #(not= (:avain %) :paallystyksen-tyovirheluettelo) valilehdet)))

(defn- kayttajaroolin-valilehdet-paallystysurakoitsija [valilehdet oikeus-vain-paallystysurakoihin? urakoitsija?]
  ;; Päällystysurakoitsijalle näytetään vain Päällystyksen työvirheluettelo -välilehti
  (if (and urakoitsija? oikeus-vain-paallystysurakoihin?)
    (filterv #(= (:avain %) :paallystyksen-tyovirheluettelo) valilehdet)
    valilehdet))

(defn- kayttajaroolin-valilehdet-paallystyksen-muu-henkilo [valilehdet oikeus-vain-paallystysurakoihin?]
  ;; Päällystysurakan muille kuin urakoitsijoille siirreään päällystyksen työvirheluettelo kärkeen
  (if oikeus-vain-paallystysurakoihin?
    (let [muokatut-valilehdet (mapv #(if (= (:avain %) :paallystyksen-tyovirheluettelo)
                                       (assoc % :jarjestys 0)
                                       %)
                                    valilehdet)]
      (jarjesta-valilehdet muokatut-valilehdet))
    valilehdet))

(defn kayttajaroolin-mukaiset-valilehdet
  "Palauttaa vain ne välilehdet, jotka ovat kyseiselle käyttäjäroolille tarpeelliset.
   Säätää myös järjestyksen kohdalleen."
  [oletusvalilehdet kayttajatiedot]
  (let [oikeus-paallystysurakkaan? (boolean (some #(= (:tyyppi %) "paallystys")
                                                  (:oikeus-urakoihin kayttajatiedot)))
        oikeus-hoitourakkaan? (boolean (some #(= (:tyyppi %) "hoito")
                                             (:oikeus-urakoihin kayttajatiedot)))
        oikeus-vain-paallystysurakoihin? (every? #(= (:tyyppi %) "paallystys")
                                                 (:oikeus-urakoihin kayttajatiedot))
        urakoitsija? (= (get-in kayttajatiedot [:organisaatio :tyyppi]) :urakoitsija)]

    (-> oletusvalilehdet
        (kayttajaroolin-valilehdet-paallystyksen-kunto oikeus-hoitourakkaan?)
        (kayttajaroolin-valilehdet-paallystyksen-tyovirheluettelo oikeus-paallystysurakkaan?)
        (kayttajaroolin-valilehdet-paallystysurakoitsija oikeus-vain-paallystysurakoihin? urakoitsija?)
        (kayttajaroolin-valilehdet-paallystyksen-muu-henkilo oikeus-vain-paallystysurakoihin?))))

(def oletusvalilehdet
  [{:avain :talvihoito
    :nimi "Talvihoito"
    :jarjestys 1
    :sisalto (:talvihoito havainnot-ryhmittain)}
   {:avain :liikennemerkit
    :nimi "Liikennemerkit"
    :jarjestys 2
    :sisalto (:liikennemerkit havainnot-ryhmittain)}
   {:avain :viherhoito
    :nimi "Viherhoito"
    :jarjestys 3
    :sisalto (:viherhoito havainnot-ryhmittain)}
   {:avain :reunat
    :nimi "Reunat"
    :jarjestys 4
    :sisalto (:reunat havainnot-ryhmittain)}
   {:avain :p-ja-l-alueet
    :nimi "P- ja L-alueet"
    :jarjestys 5
    :sisalto (:p-ja-l-alueet havainnot-ryhmittain)}
   {:avain :soratiet
    :nimi "Soratiet"
    :jarjestys 6
    :sisalto (:soratiet havainnot-ryhmittain)}
   {:avain :muut
    :nimi "Sillat"
    :jarjestys 7
    :sisalto (:sillat havainnot-ryhmittain)}
  {:avain :paallysteen-kunto ;; Koskee hoitoa
    :nimi "Pääll. kunto"
    :jarjestys 8
    :sisalto (:paallysteen-kunto havainnot-ryhmittain)}
   {:avain :paallystyksen-tyovirheluettelo ;; Koskee ylläpitoa
    :nimi "Pääll. työvirhel."
    :jarjestys 9
    :sisalto (:paallystyksen-tyovirheluettelo havainnot-ryhmittain)}])

;; Käsittelylogiikka

(defn- lisaa-liittyva-havainto!
  "Lisää havainnon ehdolle valittavaksi lomakkeella liittyväksi havainnoksi.
   Varmistaa, ettei listalla ole koskaan liikaa ehdotuksia."
  [liittyvat-havainnot-atom uusi-havainto]
  (let [max-maara-ehdotuksia 6
        uudet-liittyvat-havainnot (concat [uusi-havainto] @liittyvat-havainnot-atom)
        uudet-liittyvat-havainnot (take max-maara-ehdotuksia uudet-liittyvat-havainnot)]
    (reset! liittyvat-havainnot-atom (into [] uudet-liittyvat-havainnot))))

(defn pistemainen-havainto-painettu! [{:keys [nimi avain] :as havainto}]
  (.log js/console "Kirjataan pistemäinen havainto: " (pr-str avain))
  (reitintallennus/kirjaa-pistemainen-havainto!
    {:idxdb @s/idxdb
     :sijainti @s/sijainti
     :tr-osoite @s/tr-osoite
     :tarkastusajo-id @s/tarkastusajo-id
     :jatkuvat-havainnot @s/jatkuvat-havainnot
     :havainto-avain avain
     :epaonnistui-fn reitintallennus/merkinta-epaonnistui
     :havainto-kirjattu-fn (fn [kirjattu-havainto]
                             (lisaa-liittyva-havainto! s/liittyvat-havainnot kirjattu-havainto)
                             (ilmoitukset/ilmoita
                               (str "Pistemäinen havainto kirjattu: " nimi)
                               s/ilmoitus
                               {:tyyppi :onnistui
                                :taydennettavan-havainnon-id (:id kirjattu-havainto)}))}))

(defn valikohtainen-havainto-painettu!
  "Asettaa välikohtaisen havainnon päälle tai pois päältä."
  [{:keys [nimi avain vaatii-nappaimiston? mittaus] :as havainto}]
  ;; Jatkuva havainto ensin päälle
  (s/togglaa-jatkuva-havainto! avain)
  (.log js/console (pr-str "Välikohtaiset havainnot nyt : " @s/jatkuvat-havainnot))

  ;; Mittaus päälle jos tarvii
  (when (and vaatii-nappaimiston?
             (avain @s/jatkuvat-havainnot))
    (nappaimisto/alusta-mittaussyotto! (:tyyppi mittaus) s/mittaussyotto)
    (nappaimisto/alusta-soratiemittaussyotto! s/soratiemittaussyotto)
    (s/aseta-mittaus-paalle! (:tyyppi mittaus)))

  ;; Mittaus pois jos tarvii
  (when (and vaatii-nappaimiston?
             (not (avain @s/jatkuvat-havainnot)))
    (s/aseta-mittaus-pois!))

  (reitintallennus/kirjaa-yksittainen-reittimerkinta!
    {:idxdb @s/idxdb
     :sijainti @s/sijainti
     :tarkastusajo-id @s/tarkastusajo-id
     :jatkuvat-havainnot @s/jatkuvat-havainnot
     :mittaustyyppi @s/mittaustyyppi
     :soratiemittaussyotto @s/soratiemittaussyotto
     :epaonnistui-fn reitintallennus/merkinta-epaonnistui
     :tr-osoite @s/tr-osoite
     :havainto-avain avain
     :havainto-kirjattu-fn (fn [kirjattu-havainto]
                             (if (@s/jatkuvat-havainnot avain)
                               (ilmoitukset/ilmoita
                                 (str (or (:nimi mittaus) nimi) " alkaa")
                                 s/ilmoitus)
                               (do
                                 (ilmoitukset/ilmoita
                                   (str (or (:nimi mittaus) nimi) " päättyy")
                                   s/ilmoitus
                                   {:tyyppi :onnistui
                                    :taydennettavan-havainnon-id (:id kirjattu-havainto)})
                                 (lisaa-liittyva-havainto! s/liittyvat-havainnot kirjattu-havainto))))}))

(defn avaa-havaintolomake! []
  (.log js/console "Avataan havaintolomake!")
  (reset! s/havaintolomake-auki? true))

(defn hampurilaisvalikko-painettu! []
  (swap! s/paanavigoinnin-hampurilaisvalikon-lista-nakyvissa? not))

(defn hampurilaisvalikon-lista-item-valittu! [avain]
  (reset! s/paanavigoinnin-hampurilaisvalikon-lista-nakyvissa? false)
  (reset! s/paanavigoinnin-valittu-valilehti avain))

(defn body-click []
  (reset! s/paanavigoinnin-hampurilaisvalikon-lista-nakyvissa? false))
