(ns harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot
  (:require [clojure.string :as str]
            [harja.tiedot.urakka :as u]
            [harja.ui.viesti :as viesti]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]))

(declare tehtavan-arvo)

(defonce nakymassa? (atom false))

;; Muutosten seuranta
(defonce tallentamattomia-muutoksia (atom false))

(defn synkronoi-muutokset-muutokset-atomiin!
  "Synkronoi app-staten :tallentamattomia-muutoksia? atomiin navigaatiota varten.
   Kutsutaan automaattisesti kaikissa eventeissä, jotka muuttavat tilaa."
  [app]
  (reset! tallentamattomia-muutoksia (boolean (get app :tallentamattomia-muutoksia? false)))
  app)

(defrecord HaeTehtavatJaMaarat [parametrit])
(defrecord HaeTehtavatJaMaaratOnnistui [vastaus parametrit])
(defrecord HaeTehtavatJaMaaratEpaonnistui [vastaus parametrit])

(defrecord TallennaTehtavat [tehtavat kopioi-tuleville-vuosille?])
(defrecord TallennaTehtavatOnnistui [vastaus])
(defrecord TallennaTehtavatEpaonnistui [vastaus])

(defrecord ToggleTallennusTila [])
(defrecord FiltteroiTehtavat [hakuehto])
(defrecord PeruutaTallennus [])
(defrecord PaivitaTehtavatGrid [tehtavat])
(defrecord AvaaRivi [valiotsikko])
(defrecord NollaaTehtavatJaMaaratMuutokset [])
(defrecord TestiTallennaKaikkiinTehtaviinArvo [])

(defn hae-tehtavat-ja-maarat [_parametrit]
  (tuck-apurit/post! :hae-tehtavat-ja-maarat
    {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
     :valittu-hoitokausi @u/valittu-hoitokausi}
    {:onnistui ->HaeTehtavatJaMaaratOnnistui
     :epaonnistui ->HaeTehtavatJaMaaratEpaonnistui
     :paasta-virhe-lapi? true}))

(defn filtteroi-tehtavat
  "Palauttaa tehtavat, joiden nimi sisältää hakuehdon (case insensitive)."
  [hakuehto tehtavat]
  (if hakuehto
    (filter (fn [tehtava]
              (str/includes?
                (str/lower-case (:nimi tehtava))
                (str/lower-case hakuehto)))
      tehtavat)
    tehtavat))

(defn validoi-ja-filtteroi-tehtavat
  "Validoi hakuehdon ja filtteröi tehtävät. Näytä virheviesti, jos hakuehto on liian lyhyt (alle 2 merkkiä)."
  [hakuehto kaikki-tehtavat app]
  ;; Kun hakuehto on alle 2 merkkiä, näytetään kaikki tehtävät
  (if (>= (count hakuehto) 2)
    (let [f-tehtavat (filtteroi-tehtavat hakuehto kaikki-tehtavat)]
      (-> app
        (assoc :haku hakuehto)
        (assoc :tehtavat-ja-maarat f-tehtavat)
        (assoc :kaikki-tehtavat kaikki-tehtavat)))
    (-> app
      (assoc :tehtavat-ja-maarat kaikki-tehtavat)
      (assoc :kaikki-tehtavat kaikki-tehtavat)
      (assoc :haku hakuehto))))

(def ^:private puuttuva-tarjousmaara-viesti
  "Syötä määrä tai aseta 0. Tyhjää arvoa ei voi tallentaa.")

(defn- tyhja-tarjous-maara?
  "True jos tehtäväriviltä puuttuu sopimuksen määrä (nil/tyhjä)."
  [{:keys [tarjous_maara]}]
  (or (nil? tarjous_maara)
      (and (string? tarjous_maara) (str/blank? tarjous_maara))))

(defn- tehtavarivi?
  [{:keys [valiotsikko tehtava_id]}]
  (and (nil? valiotsikko)
       (some? tehtava_id)))

(defn- tyhjennettiinko-aiempi-tarjous-maara?
  "True jos rivillä oli aiemmin arvo ja se on nyt tyhjä.

  Periaate: sallitaan tallennus, vaikka osalla riveistä ei ole koskaan ollut arvoa,
  mutta estetään käyttäjää tyhjentämästä aiemmin syötettyä arvoa (syötä tällöin 0)."
  [tehtava-id->alkuperainen-rivi {:keys [tehtava_id] :as rivi}]
  (when (tehtavarivi? rivi)
    (let [alkuperainen (get tehtava-id->alkuperainen-rivi tehtava_id)
          alkuperainen-maara (:tarjous_maara alkuperainen)]
      (and (some? alkuperainen-maara)
           (tyhja-tarjous-maara? rivi)))))

(extend-protocol tuck/Event

  HaeTehtavatJaMaarat
  (process-event [{parametrit :parametrit} app]
    (hae-tehtavat-ja-maarat parametrit)
    (assoc app :haku-kaynnissa? true))

  HaeTehtavatJaMaaratOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (validoi-ja-filtteroi-tehtavat (:haku app) (:tehtavat vastaus) app)]
      (-> app
        (assoc :haku-kaynnissa? false)
        (assoc :haku (:haku app))
        (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
        (assoc :viimeisin-muokkaaja (:viimeisin-muokkaaja vastaus)))))

  HaeTehtavatJaMaaratEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Tietojen hakeminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :haku-kaynnissa? false)))

  TallennaTehtavat
  (process-event [{tehtavat :tehtavat kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?} app]
    (let [tehtava-id->alkuperainen-rivi (into {}
                                          (keep (fn [rivi]
                                                  (when (tehtavarivi? rivi)
                                                    [(:tehtava_id rivi) rivi])))
                                          (:kaikki-tehtavat app))
          tyhjennys-yritys? (and (not kopioi-tuleville-vuosille?)
                                 (some (partial tyhjennettiinko-aiempi-tarjous-maara?
                                                tehtava-id->alkuperainen-rivi)
                                       tehtavat))]
      (if tyhjennys-yritys?
      (do
        (viesti/nayta-toast!
          puuttuva-tarjousmaara-viesti
          :varoitus
          viesti/viestin-nayttoaika-keskipitka)
        app)
      (do
        (tuck-apurit/post! :tallenna-tehtavat-ja-maarat
          {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))
           :tehtavat tehtavat
           :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?
           :valittu-hoitokausi @u/valittu-hoitokausi}
          {:onnistui ->TallennaTehtavatOnnistui
           :epaonnistui ->TallennaTehtavatEpaonnistui
           :paasta-virhe-lapi? true})
        (assoc app :tallennus-kaynnissa? true)))))

  TallennaTehtavatOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Tiedot tallennettiin onnistuneesti.")

    (-> app
      (assoc :tallennus-kaynnissa? false)
      (assoc :tallennustila? false)
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :tehtavat-ja-maarat (filtteroi-tehtavat (:haku app) (:tehtavat vastaus)))
      (assoc :kaikki-tehtavat (:tehtavat vastaus))
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
      (synkronoi-muutokset-muutokset-atomiin!)))

  TallennaTehtavatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (-> app
      (assoc :tallennus-kaynnissa? false)))

  PaivitaTehtavatGrid
  (process-event [{tehtavat :tehtavat} app]
    (let [muokatut-tehtavat tehtavat
          yhdistetyt-tehtavat (reduce (fn [acc alkuperainen-tehtava]
                                        (let [muokattu-tehtava (first (filter #(= (:nimi %) (:nimi alkuperainen-tehtava)) muokatut-tehtavat))]
                                          (if muokattu-tehtava
                                            (conj acc muokattu-tehtava)
                                            (conj acc alkuperainen-tehtava))))
                                [] (:tehtavat-ja-maarat app))]
      (-> app
        (assoc :tallentamattomia-muutoksia? true)
        (assoc :tehtavat-ja-maarat (sort-by :jarjestys yhdistetyt-tehtavat))
        (synkronoi-muutokset-muutokset-atomiin!))))

  ToggleTallennusTila
  (process-event [_ app]
    (assoc app :tallennustila? (not (:tallennustila? app))))

  FiltteroiTehtavat
  (process-event [{hakuehto :hakuehto} app]
    (validoi-ja-filtteroi-tehtavat hakuehto (:kaikki-tehtavat app) app))

  PeruutaTallennus
  (process-event [_ app]
    (hae-tehtavat-ja-maarat nil)
    (-> app
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :tallennustila? (not (:tallennustila? app)))))

  AvaaRivi
  (process-event [{valiotsikko :valiotsikko} app]
    (let [app (if (nil? (:avatut-tehtavaryhmat app))
                (assoc app :avatut-tehtavaryhmat #{})
                app)]
      (if (contains? (:avatut-tehtavaryhmat app) valiotsikko)
        (assoc app :avatut-tehtavaryhmat (disj (:avatut-tehtavaryhmat app) valiotsikko))
        (assoc app :avatut-tehtavaryhmat (merge (:avatut-tehtavaryhmat app) valiotsikko)))))

  NollaaTehtavatJaMaaratMuutokset
  (process-event [_ app]
    (assoc app :tallentamattomia-muutoksia? false))

  TestiTallennaKaikkiinTehtaviinArvo
  (process-event [_ app]
    (let [arvolliset-tehtavat (map (fn [tehtava]
                                     (if (nil? (:valiotsikko tehtava))
                                       (assoc tehtava :tarjous_maara (tehtavan-arvo (:nimi tehtava)))
                                       tehtava))
                                   (:tehtavat-ja-maarat app))]
      (assoc app :tehtavat-ja-maarat arvolliset-tehtavat))))

(defn- tehtavan-arvo [tehtava-nimi]
  (let [arvo (case tehtava-nimi
               "Ise 1-ajorat." 9.9
               "Ise ohituskaistat" 8.1
               "Is 1-ajorat." 45.6
               "Ise 2-ajorat." 45.6
               "Is 2-ajorat." 45.6
               "Ib 2-ajorat." 45.6
               "Is ohituskaistat" 24.8
               "Is rampit" 7.6
               "Ib 1-ajorat." 54.1
               "Ic 1-ajorat" 25.1
               "II" 267.8
               "III" 218.7
               "Kävely- ja pyöräilyväylien laatukäytävät" 15
               "K1" 24.4
               "K2" 26.1
               "Levähdys- ja pysäköimisalueet" 8
               "Talvihoidon kohotettu laatu" 55.1
               "Suolaus" 900
               "Liukkaudentorjunta hiekoituksella (materiaali)" 4321
               "Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)" 4321
               "Pysäkkikatosten puhdistus" 24
               "Portaiden talvihoito" 3
               "Lisäkalustovalmius/-käyttö" 3
               "Nopeusvalvontakameroiden tolppien ja laitekoteloiden puhdistus" 15
               "Reunapaalujen kp (uusien)" 34.1
               "Reunapaalujen kunnossapito" 132.7
               "Porttaalien tarkastus ja huolto" 2
               "Vakiokokoisten liikennemerkkien uusiminen, pelkkä merkki" 425
               "Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)" 52
               "Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)" 52
               "Opastustaulun/-viitan uusiminen" 134
               "Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)" 33
               "Opastustaulujen ja opastusviittojen uusiminen portaaliin" 13
               "Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset" 25
               "Meluesteiden siisteydestä huolehtiminen" 11400
               "Töherrysten poisto" 410
               "Töherrysten estokäsittely" 440
               "Runkopuiden poisto" 1120
               "Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu" 18
               "Reunapalteen poisto" 4
               "Reunantäyttö" 200
               "Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)" 120
               "Sorateiden pinnan hoito, hoitoluokka II" 123.3
               "Sorateiden pinnan hoito, hoitoluokka III" 46.1
               "Sorapintaisten kävely- ja pyöräilyväylienhoito" 2.6
               "Sorateiden pölynsidonta (materiaali)" 120
               "Sorastus" 9123
               "Liikenteen varmistaminen kelirikkokohteessa (materiaali)" 1100
               "Soratieluokka I" 262000
               "Soratieluokka II" 812
               "Maakivien (>1m3) poisto" 51
               "Päällysteiden paikkaus - kuumapäällyste" 112
               "KT-valuasfalttipaikkaus K" 112
               "KT-valuasfalttipaikkaus T" 112
               "PAB-paikkaus käsin" 751
               "KT-reikävaluasfalttipaikkaus" 512
               "Käsin tehtävät paikkaukset pikapaikkausmassalla" 10000
               "Sirotepuhalluspaikkaus (SIPU)" 312
               "Sillan päällysteen halkeaman avarrussaumaus" 251
               "Kannukaatosaumaus" 15000
               "Kuumapäällyste" 120000
               "AB-paikkaus levittäjällä" 1000
               "Sillan kannen päällysteen päätysauman korjaukset" 251
               "Reunapalkin ja päällysteen väl. sauman tiivistäminen" 251
               "Reunapalkin liikuntasauman tiivistäminen" 251
               "Yksityisten rumpujen korjaus ja uusiminen Ø ≤ 400 mm, päällystetyt tiet" 51
               "Yksityisten rumpujen korjaus ja uusiminen Ø > 400 mm ≤ 600 mm, päällystetyt tiet" 60
               "Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm" 300
               "Päällystetyn tien rumpujen korjaus ja uusiminen Ø> 600 <= 800 mm" 200
               "Yksityisten rumpujen korjaus ja uusiminen Ø ≤ 400 mm, soratiet" 200
               "Yksityisten rumpujen korjaus ja uusiminen Ø > 400 mm ≤ 600 mm, soratiet" 200
               "Soratien rumpujen korjaus ja uusiminen Ø <= 600 mm" 150
               "Soratien rumpujen korjaus ja uusiminen Ø> 600 <=800 mm" 75
               "Avo-ojitus/päällystetyt tiet" 50000
               "Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)" 50000
               "Laskuojat/päällystetyt tiet" 5500
               "Avo-ojitus/soratiet" 45300
               "Avo-ojitus/soratiet (kaapeli kaivualueella)" 42000
               "Laskuojat/soratiet" 5725
               "Soratien runkokelirikkokorjaukset" 2100
               "Pysäkkikatoksen uusiminen" 2
               "Pysäkkikatoksen poistaminen" 3
               "Nopeusnäyttötaulun hankinta" 1
               0)]
    arvo))
