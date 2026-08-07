(ns harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot
  "Urakan muutosten tiedot - kirjatut muutokset."
  (:require
    [tuck.core :as tuck]
    [taoensso.timbre :as log]

    [harja.pvm :as pvm]
    [harja.tiedot.urakka :as u]
    [harja.ui.viesti :as viesti]
    [harja.tiedot.navigaatio :as nav]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.domain.muutos-domain :as muutos-domain]))

;; Muutostyypit:
;; - Pysyvät muutokset
;; - Muutostyö
;;   - Erillisrahoitettu
;;   - Poikkeama tehtävä- ja määräluettelon määrästä
;; - Johto- ja hallintokorvaus


;; --- Tuck-eventit ja käsittelijät ---

;; -- Pysyvät muutokset -- ALKAA
(defrecord HaePysyvanMuutoksenPohjatiedotLomakkeelle [])
(defrecord HaePysyvanMuutoksenPohjatiedotLomakkeelleOnnistui [vastaus valittu-hoitokausi])
(defrecord HaePysyvanMuutoksenPohjatiedotLomakkeelleEpaonnistui [virhe])
(defrecord PaivitaToimenpiteenTehtavamaarat [toimenpideinstanssi hk-alkuvuosi taulukon-rivit])
(defrecord PaivitaToimenpiteenTavoitehinnanMuutos [toimenpideinstanssi hk-alkuvuosi muutos-summa])
(defrecord MerkitseTehtavanMaaramuutosPoistetuksi [toimenpideinstanssi tehtava-id hk-alkuvuosi poistettu?])
(defrecord KopioiHoitovuodenMuutoksetTulevilleHoitovuosille [lahde-hk-alkuvuosi voimassa-alkaen urakan-hoitovuodet])

;; Peruuttaa tavoite-ja kattohinnan vahvistuksen Kustannussuunnitelmassa (Hoitovuoden alun tavoitehinta)
(defrecord PeruutaTavoiteJaKattohinta [hk-alkuvuosi])
(defrecord PeruutaTavoiteJaKattohintaOnnistui [vastaus hk-alkuvuosi])
(defrecord PeruutaTavoiteJaKattohintaEpaonnistui [virhe])

(defrecord PaivitaTehtavavaikutus [rivi hk-alkuvuosi])
(defrecord PaivitaTehtavavaikutusSyy [toimenpideinstanssi syy hk-alkuvuosi])

(defn pysyvia-muutoksia-tulevilla-hoitovuosilla?
  "Hakee pysyvän muutoksen tiedoista, onko muutoksia tulevilla hoitovuosilla."
  [hoitovuosi muokattava-muutos]
  (let [alkuvuosi (some-> hoitovuosi (first) (pvm/vuosi))
        toimenpiteiden-tiedot (:toimenpiteiden-tiedot muokattava-muutos)
        ;; Hakee kaukaisimman alkuvuoden, jolta löytyy muutoksia pysyvästä muutoksesta
        max-alkuvuosi (when (seq toimenpiteiden-tiedot)
                        (->> toimenpiteiden-tiedot
                          (mapcat (fn [rivi]
                                    (concat
                                      (map :hoitokauden_alkuvuosi (:tehtavat_ja_maarat rivi))
                                      (map :hoitokauden_alkuvuosi (:kustannusvaikutukset rivi)))))
                          (remove nil?)
                          (apply max)))]
    (< alkuvuosi (or max-alkuvuosi 0))))

(defn muokkaa-toimenpiteen-rivit-pysyva-muutos
  "Palauttaa app-tilan, jossa yhden toimenpideinstanssin vetolaatikon rivejä on muokattu muokkaus-fn avulla."
  [app toimenpideinstanssi muokkaus-fn]
  (update-in app [:muokattava-muutos :toimenpiteiden-tiedot]
    (fn [rivit]
      (mapv (fn [rivi]
              (if (= toimenpideinstanssi (:toimenpideinstanssi rivi))
                (muokkaus-fn rivi)
                rivi))
        rivit))))

(defn koosta-tehtavat-ja-maarat-pysyvaan-muutokseen
  "Koostetaan tehtävien määrämuutokset kaikista toimenpide-vetolaatikoista yhteen vektoriksi tallennusta varten."
  [app]
  ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
  (assoc-in app [:muokattava-muutos :tehtavat_ja_maarat]
    (->> (map :tehtavat_ja_maarat (get-in app [:muokattava-muutos :toimenpiteiden-tiedot]))
      (flatten)
      (vec))))

(defn luo-tai-paivita-kustannusvaikutus
  "Rakentaa / päivittää kustannusvaikutusrivin perusavaimilla."
  [{:keys [toimenpideinstanssi hoitokauden_alkuvuosi summa tehtavamaaramuutos-kirjattu?]} vanha-kv]
  (assoc (or vanha-kv {})
    :toimenpideinstanssi toimenpideinstanssi
    :hoitokauden_alkuvuosi hoitokauden_alkuvuosi
    :kustannuslaji "hankintakustannukset"
    :summa summa
    :tehtavamaaramuutos-kirjattu? tehtavamaaramuutos-kirjattu?))

(defn paivita-kustannusvaikutus [r toimenpideinstanssi hk-alkuvuosi f]
  (update r :kustannusvaikutukset
    (fn [kustannusvaikutukset]
      (mapv
        (fn [kv]
          (if (and (= (:toimenpideinstanssi kv) toimenpideinstanssi)
                (= (:hoitokauden_alkuvuosi kv) hk-alkuvuosi))
            (f kv)
            kv))
        kustannusvaikutukset))))

(defn koosta-kustannusvaikutukset-pysyvaan-muutokseen
  "Koostetaan kustannusvaikutukset kaikista toimenpide-vetolaatikoista yhteen vektoriksi tallennusta varten."
  [app]
  ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
  (assoc-in app [:muokattava-muutos :kustannusvaikutukset]
    (->> (get-in app [:muokattava-muutos :toimenpiteiden-tiedot])
      (mapcat :kustannusvaikutukset)
      vec)))

;; -- Apureita tehtävien määrämuutosten ja kustannusvaikutusten kopiointiin hoitovuodelle
(defn- korvaa-rivi-tai-merkitse-poistetuksi
  "Merkitse rivit poistetuksi tai korvaa tehtava-avainta vastaava rivi uusilla tiedoilla lähdehoitovuodelta."
  [lahderivit kohderivi]
  ;; Etsitään korvaava rivi lähderivien joukosta tehtava-avaimen perusteella
  (let [korvaava-rivi (some #(when (= (:tehtava kohderivi) (:tehtava %)) %) lahderivit)]
    (cond
      korvaava-rivi
      (assoc korvaava-rivi :korvattu? true)
      ;; Jos rivi on täysin uusi (eli vain UI:n tilassa), niin ei merkitä poistetuksi, vaan poistetaan kokonaan UI:sta
      ;; Muutoin, ohjeistetaan backendiä poistamaan rivi tietokannasta
      (not (:uusi? kohderivi))
      (assoc kohderivi :poistettu true)
      ;; Vain UI:n tilassa olevat rivit (eli :uusi? true) rivit suodatetaan pois
      :else nil)))

(defn- muunna-tehtava-ja-maara-rivit-kohdevuodelle
  "Muuntaa lahde- ja kohderivejä siten, että kopiointi kohdehoitovuodelle onnistuu ja vanhat rivit kohdevuodelta poistetaan."
  [lahderivit kohderivit kohdevuosi]
  (let [
        ;; Käydään kohdevuoden rivit läpi. Rivit joko korvataan vastaavien lähderivien tiedoilla tai poistetaan
        kohderivit (remove nil?
                     (mapv
                       (fn [rivi]
                         (korvaa-rivi-tai-merkitse-poistetuksi lahderivit rivi))
                       kohderivit))
        ;; Lahderiveistä poistetaan ne rivit, jotka on jo korvattu kohderiveihin
        ;; Myöskään lähderiveissä poistetuksi merkittyjä rivejä ei saa kopioida kohdevuodelle
        lahderivit (remove #(some (fn [kohderivi]
                                    (or
                                      (= (:tehtava kohderivi) (:tehtava %))
                                      (:poistettu %)))
                              kohderivit)
                     lahderivit)
        ;; Yhdistetään rivien joukot. Näistä tulee lopullinen rivijoukko, joka kopioidaan kohdehoitovuodelle.
        uudet-rivit (concat kohderivit lahderivit)]

    ;; Lisätään vielä riveihin oikea kohdevuotta vastaava hoitovuosi
    (mapv (fn [rivi]
            (-> rivi
              (assoc :hoitokauden_alkuvuosi kohdevuosi)))
      uudet-rivit)))

(defn- korvaa-vuosien-tehtavat-ja-maara-rivit
  "Korvaa kohdevuosien tehtävä- ja määrärivit kopiolla lähdevuoden riveistä.
   Järjestää rivit hoitovuoden mukaan."
  [tehtavat-ja-maarat lahdevuosi vuodet]
  (let [tjm-per-vuosi-map (group-by :hoitokauden_alkuvuosi tehtavat-ja-maarat)
        lahderivit (get tjm-per-vuosi-map lahdevuosi)]
    (if (or (empty? lahderivit) (empty? vuodet))
      tehtavat-ja-maarat
      (->> (reduce (fn [m vuosi]
                     ;; Korvaa vuoden tehtävä- ja määrärivit lähderiveillä, aseta uusi alkuvuosi
                     (assoc m vuosi (concat
                                      ;; Korvataan kohdevuoden rivit lähderiveillä tai poistetaan sellaisia
                                      ;; kohdevuoden rivejä, joita ei löydy lähderivien joukosta.
                                      (muunna-tehtava-ja-maara-rivit-kohdevuodelle lahderivit (get m vuosi) vuosi))))
             tjm-per-vuosi-map
             vuodet)
        (sort-by first)
        (mapcat (comp vec second))
        (vec)))))

(defn- korvaa-vuosien-kustannusvaikutukset
  "Korvaa kohdevuosien kustannusvaikutukset kopiolla lähdevuoden riveistä (jos löytyy).
   Järjestää tuloksen hoitovuoden mukaan."
  [kustannusvaikutukset lahdevuosi vuodet]
  (let [kvt-per-vuosi-map (group-by :hoitokauden_alkuvuosi kustannusvaikutukset)
        lahde-rivi (some-> (get kvt-per-vuosi-map lahdevuosi) first)]
    (if (or (nil? lahde-rivi) (empty? vuodet))
      kustannusvaikutukset
      (->> (reduce (fn [m vuosi]
                     ;; Korvaa vuoden kustannusvaikutus lähderivin tiedolla, aseta uusi alkuvuosi
                     (assoc m vuosi [(assoc lahde-rivi :hoitokauden_alkuvuosi vuosi)]))
             kvt-per-vuosi-map
             vuodet)
        (sort-by first)
        (mapcat second)
        (vec)))))

(defn kopioi-hoitovuoden-muutokset-toimenpiteen-riville
  "Kopioi yhden toimenpiteen vetolaatikko-riville lähdevuoden muutosrivit kaikille tuleville vuosille.
  (Yksi 'vetolaatikkorivi' sisältää yhteen toimenpiteeseen liittyviä tehtävä-ja määrämuutoksia ja kustannusvaikutuksia.)
  Käytä tilan debug-työkalua ymmärtääksesi tietorakenteen paremmin."
  [rivi lahdevuosi urakan-hoitovuodet]
  ;; Haetaan urakan vuosista tulevat vuodet, eli lähtövuotta suuremmat vuodet
  (let [tulevat-vuodet (filter #(> % lahdevuosi) urakan-hoitovuodet)]
    (if (empty? tulevat-vuodet)
      rivi
      (-> rivi
        (update :tehtavat_ja_maarat #(korvaa-vuosien-tehtavat-ja-maara-rivit % lahdevuosi tulevat-vuodet))
        (update :kustannusvaikutukset #(korvaa-vuosien-kustannusvaikutukset % lahdevuosi tulevat-vuodet))))))


;; -- Pysyvät muutokset -- LOPPUU


(extend-protocol tuck/Event
  ;; -- Pysyvät muutokset -- ALKAA

  HaePysyvanMuutoksenPohjatiedotLomakkeelle
  (process-event [_ app]
    (log/debug "HaePysyvanMuutoksenPohjatiedotLomakkeelle")

    (let [valittu-hoitokausi (:valittu-hoitokausi app)]
      (tuck-apurit/post! :hae-pysyvan-muutoksen-pohjatiedot
        {:urakka-id @nav/valittu-urakka-id
         :muutos-id nil
         :muutos-versio nil}
        {:onnistui ->HaePysyvanMuutoksenPohjatiedotLomakkeelleOnnistui
         :onnistui-parametrit [valittu-hoitokausi]
         :epaonnistui ->HaePysyvanMuutoksenPohjatiedotLomakkeelleEpaonnistui}))
    (-> app
      (assoc
        :muutoksen-tiedot-haku-kaynnissa? true)))

  HaePysyvanMuutoksenPohjatiedotLomakkeelleOnnistui
  (process-event [{valittu-hoitokausi :valittu-hoitokausi vastaus :vastaus} app]
    (log/debug "HaePysyvanMuutoksenPohjatiedotLomakkeelleOnnistui")

    ;; Pysyviä muutoksia voi kirjata vain 2025 alkaen
    (let [mahdolliset-hoitovuodet (:urakan-hoitokaudet app)]
      (-> app
        (dissoc :muutoksen-tiedot-haku-kaynnissa?)
        (assoc-in [:muokattava-muutos :toimenpiteiden-tiedot] (:toimenpiteiden-tiedot vastaus))
        (assoc-in [:muokattava-muutos :toimenpiteiden-tehtavat] (:toimenpiteiden-tehtavat vastaus))
        (assoc-in [:muokattava-muutos :mahdolliset-hoitovuodet-lomakkeella] mahdolliset-hoitovuodet)
        (assoc-in [:muokattava-muutos :liitteet] [])
        (assoc-in [:muokattava-muutos :tehtavat_ja_maarat] [])
        (assoc-in [:muokattava-muutos :kustannusvaikutukset] [])
        ;; Asetetaan suoraan Muutos-näkymässä valittu hoitokausi pysyvän muutoksen hoitovuodeksi
        (assoc-in [:muokattava-muutos :hoitovuosi] valittu-hoitokausi))))

  HaePysyvanMuutoksenPohjatiedotLomakkeelleEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Pysyvän muutoksen taustatietojen hakeminen epäonnistui!" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (dissoc app :muutoksen-tiedot-haku-kaynnissa?))

  PaivitaToimenpiteenTehtavamaarat
  (process-event [{toimenpideinstanssi :toimenpideinstanssi
                   hk-alkuvuosi :hk-alkuvuosi
                   ;; Yhden vetolaatikon tehtävät-ja-määrät rivit valitulta hoitovuodelta
                   taulukon-rivit :taulukon-rivit} app]
    (assert (int? toimenpideinstanssi))
    (assert (int? hk-alkuvuosi))

    (-> app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (muokkaa-toimenpiteen-rivit-pysyva-muutos toimenpideinstanssi
        (fn [rivi]
          ;; Päivitetään vain valitun toimenpideinstanssin tehtävät ja määrät, ja ainoastaan valitun hoitokauden osalta
          (update rivi :tehtavat_ja_maarat
            (fn [tehtavat-ja-maarat]
              (let [;; Suodatetaan vanhat rivit pois valitulta hoitokaudelta
                    tehtavat-ja-maarat (filterv #(not= hk-alkuvuosi (:hoitokauden_alkuvuosi %)) tehtavat-ja-maarat)
                    ;; Lisätään tilalle uudet rivit valitulta hoitokaudelta (tuplavarmistus filtteröinnillä, että mukana tulee vain valitun hoitokauden rivit)
                    tehtavat-ja-maarat (into tehtavat-ja-maarat (filterv #(= hk-alkuvuosi (:hoitokauden_alkuvuosi %)) taulukon-rivit))]
                ;; Palautetaan päivitetyt tehtävät ja määrät
                tehtavat-ja-maarat)))))

      ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
      (koosta-tehtavat-ja-maarat-pysyvaan-muutokseen)

      ;; Salli lomakkeen tallennus
      (assoc :voi-tallentaa? true)))

  MerkitseTehtavanMaaramuutosPoistetuksi
  (process-event [{toimenpideinstanssi :toimenpideinstanssi
                   tehtava-id :tehtava-id
                   hk-alkuvuosi :hk-alkuvuosi
                   poistettu? :poistettu?} app]
    (-> app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (muokkaa-toimenpiteen-rivit-pysyva-muutos toimenpideinstanssi
        (fn [rivi]
          ;; Päivitetään vain valitun toimenpideinstanssin tehtävät ja määrät, ja ainoastaan valitun hoitokauden osalta
          (update rivi :tehtavat_ja_maarat
            (fn [tehtavat-ja-maarat]
              (into []
                (keep (fn [rivi]
                        (cond
                          (and
                            (= tehtava-id (:tehtava rivi))
                            (= hk-alkuvuosi (:hoitokauden_alkuvuosi rivi)))
                          ;; Uusia vain UI:ssa olemassaolevia rivejä ei merkitä poistetuksi, ne poistetaan kokonaan UI:sta
                          ;; Tässä uudet rivit asetetaan nil:ksi ja poistetaan lopputuloksesta
                          (when (not (:uusi? rivi))
                            ;; Tallentaessa backend-logiikka suorittaa tarvittavat toimenpiteet poistetuille riveille
                            (assoc rivi :poistettu poistettu?))

                          :else rivi))
                  tehtavat-ja-maarat))))))

      ;; Yhdistä tehtavat ja määrät kaikista vetolaatikoista tallennusta varten
      (koosta-tehtavat-ja-maarat-pysyvaan-muutokseen)

      ;; Salli lomakkeen tallennus
      (assoc :voi-tallentaa? true)))

  PaivitaToimenpiteenTavoitehinnanMuutos
  (process-event [{muutos-summa :muutos-summa
                   toimenpideinstanssi :toimenpideinstanssi
                   hk-alkuvuosi :hk-alkuvuosi} app]
    (assert (int? hk-alkuvuosi))

    (-> app
      ;; Nämä pitävät gridin tilan synkassa app-tilan kanssa
      (muokkaa-toimenpiteen-rivit-pysyva-muutos toimenpideinstanssi
        (fn [rivi]
          ;; Päivitetään valitun toimenpideinstanssin kustannusvaikutukset
          (update rivi :kustannusvaikutukset
            (fn [kustannusvaikutukset]
              (let [kv-map (into {} (map (juxt :hoitokauden_alkuvuosi identity)
                                      kustannusvaikutukset))
                    ;; Päivitetään (tai luodaan) valitun hoitokauden kustannusvaikutus
                    kv-map (update kv-map hk-alkuvuosi
                             (fn [arvo]
                               (luo-tai-paivita-kustannusvaikutus
                                 {:summa muutos-summa
                                  :toimenpideinstanssi toimenpideinstanssi
                                  :hoitokauden_alkuvuosi hk-alkuvuosi
                                  :tehtavamaaramuutos-kirjattu? true}
                                 arvo)))]
                (-> kv-map vals vec))))))

      ;; Yhdistä kustannusvaikutukset kaikista vetolaatikoista tallennusta varten
      (koosta-kustannusvaikutukset-pysyvaan-muutokseen)

      ;; Salli lomakkeen tallennus
      (assoc :voi-tallentaa? true)))

  ;; Kopioi pysyvän muutoksen tiedot valitulta hoitovuodelta kaikille tuleville hoitovuosille
  KopioiHoitovuodenMuutoksetTulevilleHoitovuosille
  (process-event [{lahde-hk-alkuvuosi :lahde-hk-alkuvuosi
                   voimassa-alkaen :voimassa-alkaen
                   urakan-hoitovuodet :urakan-hoitovuodet} app]
    (log/debug "KopioiHoitovuodenMuutoksetTulevilleHoitovuosille, lähdehoitovuosi: " lahde-hk-alkuvuosi)
    (assert (int? lahde-hk-alkuvuosi))
    (assert (pvm/pvm? voimassa-alkaen))
    (assert (sequential? urakan-hoitovuodet))

    (viesti/nayta-toast! "Tiedot kopioitu tuleville hoitovuosille lomakkeella.")

    (let [{:keys [tavoitehinta-indeksikorjattu-per-hoitovuosi]} (:budjettitavoitteet app)
          ;; Filtteröidään pois kohdehoitovuosista ne, joiden muokkaukset on lukittu pysyvässä muutoksessa
          ;; Eli, tavoitehinta on vahvistettu tai välikatselmuksen päätöksiä on tehty ko. hoitovuodelle
          lukitsemattomat-hoitovuodet (filterv
                                        #(not (muutos-domain/pysyva-muutos-hoitovuosi-lukittu?
                                                tavoitehinta-indeksikorjattu-per-hoitovuosi voimassa-alkaen %))
                                        urakan-hoitovuodet)
          kohde-hk-alkuvuodet (map #(some-> % first pvm/vuosi) lukitsemattomat-hoitovuodet)]
      (-> app
        (update-in [:muokattava-muutos :toimenpiteiden-tiedot]
          (fn [rivit]
            (mapv #(kopioi-hoitovuoden-muutokset-toimenpiteen-riville
                     % lahde-hk-alkuvuosi kohde-hk-alkuvuodet)
              rivit)))

        ;; Yhdistä tehtavat ja määrät, sekä kustannusvaikutukset kaikista vetolaatikoista tallennusta varten
        (koosta-tehtavat-ja-maarat-pysyvaan-muutokseen)
        (koosta-kustannusvaikutukset-pysyvaan-muutokseen)

        ;; Salli lomakkeen tallennus
        (assoc :voi-tallentaa? true))))

  PeruutaTavoiteJaKattohinta
  (process-event [{hk-alkuvuosi :hk-alkuvuosi} app]
    (assert (int? hk-alkuvuosi))

    (tuck-apurit/post! :vahvista-tavoite-ja-kattohinta
      {:urakka-id @nav/valittu-urakka-id
       :hoitovuoden-alkuvuosi hk-alkuvuosi
       :vahvista? false}
      {:onnistui ->PeruutaTavoiteJaKattohintaOnnistui
       :onnistui-parametrit [hk-alkuvuosi]
       :epaonnistui ->PeruutaTavoiteJaKattohintaEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  PeruutaTavoiteJaKattohintaOnnistui
  (process-event [{hk-alkuvuosi :hk-alkuvuosi vastaus :vastaus} app]
    (assert (int? hk-alkuvuosi))

    (if (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])
      (viesti/nayta-toast!
        "Tavoite- ja kattohinnan peruminen epäonnistui!"
        :varoitus
        viesti/viestin-nayttoaika-keskipitka)
      (viesti/nayta-toast! "Tavoite- ja kattohinnan vahvistus peruttu."))

    ;; Päivitä app-tilaan tieto, että hoitovuoden vahvistukset on purettu
    (-> app
      (assoc-in [:budjettitavoitteet :tavoitehinta-indeksikorjattu-per-hoitovuosi hk-alkuvuosi] false)))

  PeruutaTavoiteJaKattohintaEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast!
      "Tavoite- ja kattohinnan vahvistuksen peruminen epäonnistui!"
      :varoitus
      viesti/viestin-nayttoaika-keskipitka)
    app)

  PaivitaTehtavavaikutus
  (process-event [{:keys [rivi hk-alkuvuosi]} app]
    (let [toimenpideinstanssi (:toimenpideinstanssi rivi)]
      (assert (int? hk-alkuvuosi))

      (-> app
        (assoc :voi-tallentaa? true)
        (muokkaa-toimenpiteen-rivit-pysyva-muutos
          toimenpideinstanssi
          (fn [r]
            (paivita-kustannusvaikutus
              r
              toimenpideinstanssi
              hk-alkuvuosi
              #(update % :tehtavamaaramuutos-kirjattu?
                 (fn [v]
                   (if (some? v) (not v) false))))))
        (koosta-kustannusvaikutukset-pysyvaan-muutokseen))))

  PaivitaTehtavavaikutusSyy
  (process-event [{:keys [toimenpideinstanssi syy hk-alkuvuosi]} app]
    (assert (int? hk-alkuvuosi))

    (-> app
      (assoc :voi-tallentaa? true)
      (muokkaa-toimenpiteen-rivit-pysyva-muutos
        toimenpideinstanssi
        (fn [r]
          (paivita-kustannusvaikutus
            r
            toimenpideinstanssi
            hk-alkuvuosi
            #(assoc % :syy syy))))
      (koosta-kustannusvaikutukset-pysyvaan-muutokseen))))


;; -- Pysyvät muutokset -- LOPPUU

