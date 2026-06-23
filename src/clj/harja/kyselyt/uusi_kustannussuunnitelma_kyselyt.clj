(ns harja.kyselyt.uusi-kustannussuunnitelma-kyselyt
  (:require [harja.kyselyt.budjettisuunnittelu :as budjettisuunnittelu-q]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [harja.tyokalut.yleiset :refer [round2] :as yleiset]
            [harja.domain.mhu :as mhu]
            [harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain :as kust-domain]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.indeksit :as indeksi-kyselyt]
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]
            [harja.kyselyt.kustannusarvioidut-tyot :as ka-q]
            [harja.kyselyt.kiinteahintaiset-tyot :as kiint-kyselyt]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuva-kyselyt]))

(defqueries "harja/kyselyt/uusi_kustannussuunnitelma_kyselyt.sql"
  {:positional? true})

(declare hae-urakan-toimenpiteet hae-kiintea-kustannus-toimenpiteelle-kuukaudelta
  hae-kiintea-kustannus-kuukausittain hae-pysyvat-hankintakus-muutokset poista-kiinteat-kustannukset-kuukausittain!
  tallenna-kiinteat-kustannukset-kuukaudelta<! paivita-kiinteat-kustannukset-kuukausittain<!
  hae-viimeisin-muokkaaja-kiinteahintaiselle-kustannukselle
  hae-erillishankinta-kuukausittain hae-kuukauden-erillishankinta hae-tallennetun-kuukauden-erillishankinta
  paivita-kuukauden-erillishankinta<! tallenna-kuukauden-erillishankinta<!
  hae-viimeisin-muokkaaja-erillishankinnoille
  hae-hoidonjohtopalkkiot-kuukausittain hae-olemassa-oleva-hoidonjohtopalkkio hae-viimeisin-muokkaaja-hoidonjohtopalkkiolle
  hae-rahavaraus-vuodelta
  paivita-kuukauden-hoidonjohtopalkkio<! tallenna-kuukauden-hoidonjohtopalkkio<!
  hae-johto-ja-hallintokorvaukset-kuukausittain
  hae-tallennettu-kuukauden-johto-ja-hallintokorvaus
  hae-johto-ja-hallintokorvaukset-2019-mhu
  hae-kuukauden-johto-ja-hallintokorvaus hae-toimenkuvan-kuukauden-johto-ja-hallintokorvaus
  hae-toimenkuvan-johto-ja-hallintokorvaukset-kuukausittain
  hae-muut-kulut-toimenkuviin-kuukausittain hae-muut-kulut-kuukaudelle
  lisaa-kuukauden-muu-kulu<! paivita-kuukauden-muu-kulu<!
  hae-tehtava-tunnisteella
  paivita-kuukauden-johto-ja-hallintokorvaus<!
  hae-urakan-hoitovuoden-tarjous hae-vanhan-urakan-hoitovuoden-tarjous
  lisaa-kuukauden-johto-ja-hallintokorvaus<! hae-viimeisin-muokkaaja-jjh
  vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille!
  vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille!
  vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille!
  vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille!
  indeksikorjaukset-vahvistettu? paivita-tavoite-ja-kattohinta<!
  lisaa-tavoite-ja-kattohinta<! hae-urakan-hoitovuoden-tavoitetiedot
  hae-kustannussuunnitelman-osiot lisaa-kustannussuunnitelma-osio paivita-kustannussuunnitelma-osio
  tulevilla-hoitovuosilla-arvoja? aseta-kasin-syotetty-kattohinta<!
  paivita-kasin-syotetty-kattohinta! hae-laskutusrajan-tarkistukset)

(defn laske-indeksikorjattu-summa
  "Indeksikorjattu summa lasketaan summasta ja urakan voimassaolevista indekseistä. Jos summaa ei ole annettu, palautetaan nil."
  [summa urakan-indeksit hoitovuosi-nro]
  (when summa
    (indeksi-kyselyt/indeksikorjaa (indeksi-kyselyt/indeksikerroin urakan-indeksit hoitovuosi-nro) summa)))

(defn hae-kiinteat-kustannukset [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Haetaan urakan toimenpiteet
        toimenpiteet (hae-urakan-toimenpiteet db {:urakkaid urakka-id})
        ;; Kiinteähintaiset kustannukset
        kiinteat (reduce (fn [acc {:keys [nimi koodi toimenpideinstanssi-id jarjestys]}]
                           (let [kiinteat (hae-kiintea-kustannus-kuukausittain
                                            db {:sopimus-id sopimus-id
                                                :vuosi hoitovuoden-alkuvuosi
                                                :toimenpideinstanssi-id toimenpideinstanssi-id})

                                 pysyvat (hae-pysyvat-hankintakus-muutokset
                                           db {:sopimus-id sopimus-id
                                               :urakka urakka-id
                                               :vuosi hoitovuoden-alkuvuosi
                                               :toimenpideinstanssi-id toimenpideinstanssi-id})

                                 kiinteat-alkukausi (filter #(>= (:kuukausi %) 10) kiinteat)
                                 kiinteat-loppukausi (filter #(<= (:kuukausi %) 9) kiinteat)
                                 alkukausi (if (seq kiinteat-alkukausi) (apply + (map (fn [rivi]
                                                                                        (if (:summa rivi)
                                                                                          (:summa rivi)
                                                                                          0))
                                                                                   kiinteat-alkukausi)) 0)
                                 alkukausi-indeksikorjattu (if (seq kiinteat-alkukausi)
                                                             (apply + (map (fn [rivi]
                                                                             (if (:summa_indeksikorjattu rivi)
                                                                               (:summa_indeksikorjattu rivi)
                                                                               0))
                                                                        kiinteat-alkukausi))
                                                             0)
                                 loppukausi (if (seq kiinteat-loppukausi) (apply + (map (fn [rivi]
                                                                                          (if (:summa rivi)
                                                                                            (:summa rivi)
                                                                                            0))
                                                                                     kiinteat-loppukausi)) 0)
                                 loppukausi-indeksikorjattu (if (seq kiinteat-loppukausi)
                                                              (apply + (map (fn [rivi]
                                                                              (if (:summa_indeksikorjattu rivi)
                                                                                (:summa_indeksikorjattu rivi)
                                                                                0))
                                                                         kiinteat-loppukausi))
                                                              0)]
                             (conj acc {:nimi nimi
                                        :koodi koodi
                                        :jarjestys jarjestys
                                        :toimenpideinstanssi-id toimenpideinstanssi-id
                                        :alkukausi alkukausi
                                        :alkukausi-indeksikorjattu alkukausi-indeksikorjattu
                                        :loppukausi loppukausi
                                        :loppukausi-indeksikorjattu loppukausi-indeksikorjattu
                                        :yhteensa (+ alkukausi loppukausi)
                                        :yhteensa-indeksikorjattu (+ alkukausi-indeksikorjattu loppukausi-indeksikorjattu)
                                        :pysyvat-muutokset (or (some-> pysyvat first :summa) "Ei muutoksia")})))
                   []
                   toimenpiteet)

        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-kiinteahintaiselle-kustannukselle
                                    db {:sopimus-id sopimus-id
                                        :vuosi hoitovuoden-alkuvuosi
                                        :urakkaid urakka-id}))

        pysyvat-muutokset-yht (bigdec (apply + (filter number? (map :pysyvat-muutokset kiinteat))))

        ;; Yhteenvetorivi
        yhteenveto {:nimi "Yhteensä"
                    :kaikki-alkukausi (apply + (map :alkukausi kiinteat))
                    :kaikki-alkukausi-indeksikorjattu (apply + (map :alkukausi-indeksikorjattu kiinteat))
                    :kaikki-loppukausi (apply + (map :loppukausi kiinteat))
                    :kaikki-loppukausi-indeksikorjattu (apply + (map :loppukausi-indeksikorjattu kiinteat))
                    :kaikki-yhteensa (+ (apply + (map :alkukausi kiinteat)) (apply + (map :loppukausi kiinteat)))
                    :kaikki-yhteensa-indeksikorjattu (+ (apply + (map :alkukausi-indeksikorjattu kiinteat)) (apply + (map :loppukausi-indeksikorjattu kiinteat)))
                    :kaikki-pysyvat-muutokset (if (> pysyvat-muutokset-yht 0.0M) pysyvat-muutokset-yht "Ei muutoksia")
                    :jarjestys 999999999 ;; Joku iso luku, jolla saadaan yhteenvetorivi listan loppuun
                    :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                    :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}

        kiinteat (vec (sort-by :jarjestys (conj kiinteat yhteenveto)))]
    kiinteat))

(defn hae-rahavaraukset
  "Haetaan rahavaraukset tietokannsta kustannussuunnitelmaan.
  Saadaan [{:nimi <rahavarausnimi> :summa <summa> :summa-indeksikorjattu nil} ...]"
  [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [rahavaraukset (hae-rahavaraus-vuodelta db {:urakkaid urakka-id
                                                   :sopimusid sopimus-id
                                                   :vuosi hoitovuoden-alkuvuosi})]
    rahavaraukset))

(defn hae-hoidonjohtopalkkiot [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoidonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))
        hoidonjohtopalkkiot (when hoidonjohto-tpi-id
                              (hae-hoidonjohtopalkkiot-kuukausittain db
                                {:sopimus-id sopimus-id
                                 :vuosi hoitovuoden-alkuvuosi
                                 :tehtava-id (:id tehtava)
                                 :toimenpideinstanssi-id hoidonjohto-tpi-id}))
        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-hoidonjohtopalkkiolle
                                    db {:sopimus-id sopimus-id
                                        :vuosi hoitovuoden-alkuvuosi
                                        :tehtava-id (:id tehtava)
                                        :toimenpideinstanssi-id hoidonjohto-tpi-id}))
        hoidonjohtopalkkiot (if (seq hoidonjohtopalkkiot)
                              ;; Jos on tallennettu jo hoidonjohtopalkkioita, niin lisätään niihin kalenterikuukausi
                              (map (fn [rivi]
                                     (merge rivi
                                       {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                             (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                        :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                        :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                hoidonjohtopalkkiot)
                              ;; Jos ei ole tallennettu hoidonjohtopalkkioita, niin luodaan nolla arvot
                              (mapv (fn [kk]
                                      (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                        {:id nil
                                         :sopimus sopimus-id
                                         :tehtava (:id tehtava)
                                         :toimenpideinstanssi hoidonjohto-tpi-id
                                         :kuukausi kk
                                         :vuosi vuosi
                                         :summa 0
                                         :summa_indeksikorjattu nil
                                         :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                         :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                         :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                [10 11 12 1 2 3 4 5 6 7 8 9]))]
    (sort-by (juxt :vuosi :kuukausi) hoidonjohtopalkkiot)))

(defn paattele-toimenkuvan-kuukaudet [urakan-alkuvuosi toimenkuva]
  (let [toimenkuva-nimi (:toimenkuva toimenkuva)
        toimenkuva-nimike (:nimike toimenkuva)]
    (cond
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "sopimusvastaava")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "vastuunalainen työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Päätoiminen apulainen (talvikausi)")) [5 6 7 8 9]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Päätoiminen apulainen (kesäkausi)")) [10 11 12 1 2 3 4]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Apulainen/työnjohtaja (talvikausi)")) [5 6 7 8 9]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimike "Apulainen/työnjohtaja (kesäkausi)")) [10 11 12 1 2 3 4]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "viherhoidosta vastaava henkilö")) [4 5 6 7 8]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "hankintavastaava")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (<= urakan-alkuvuosi 2021) (= toimenkuva-nimi "harjoittelija")) [5 6 7 8]

      (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "valmistelukausi ennen urakka-ajan alkua")) [10] ;; Tämä pitäisi olla ennen sopimuskautta. Mutta laitetaan sinne yksi kuukausi
      (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "vastuunalainen työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "päätoiminen apulainen")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "apulainen/työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "viherhoidosta vastaava henkilö")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "hankintavastaava")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (and (>= urakan-alkuvuosi 2022) (<= urakan-alkuvuosi 2024)) (= toimenkuva-nimi "harjoittelija")) [10 11 12 1 2 3 4 5 6 7 8 9]

      (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "valmistelukausi ennen urakka-ajan alkua")) [8] ;; Tämäkin pitäisi olla ennen sopimuskautta. Mutta laitetaan sinne yksi kuukausi
      (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "vastuunalainen työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "2. työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "3. työnjohtaja")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "viherhoidosta vastaava henkilö")) [10 11 12 1 2 3 4 5 6 7 8 9]
      (and (= urakan-alkuvuosi 2024) (= toimenkuva-nimi "harjoittelija")) [10 11 12 1 2 3 4 5 6 7 8 9]
      :else [10 11 12 1 2 3 4 5 6 7 8 9])))

(defn hae-johto-ja-hallintokorvaukset-2019-2024 [db urakka-id sopimus-id hoitovuoden-alkuvuosi urakan-alkuvuosi toimenkuvat-tarjouksesta]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitovuosi-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-jjh
                                    db {:urakka-id urakka-id
                                        :vuosi hoitovuoden-alkuvuosi}))
        ;; Haetaan ensin urakkakohtaiset toimenkuvat
        toimenkuvat (toimenkuva-kyselyt/hae-urakan-toimenkuvat-alkuvuoden-perusteella db {:urakka-id urakka-id
                                                                                          :urakan-alkuvuosi urakan-alkuvuosi})
        ;; 2019 - 2021 alkavien urakoiden toimenkuvat eivät löydy tietokantahaulla, koska ne on kovakoodattu fronttiin. Niille on kuitenkin annettu
        ;; joissain tapauksissa kaksi nimeä, mutta sama id. Joten joudumme taaksepäin yhteensopivuuden vuoksi tekemään muunnoksen
        toimenkuvat (if (<= urakan-alkuvuosi 2021)
                      (reduce (fn [uudet-toimenkuvat toimenkuva]
                                (let [uusi-toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                                        {:toimenkuva "päätoiminen apulainen"
                                                         :nimike "Päätoiminen apulainen (talvikausi)"
                                                         :id (:id toimenkuva)
                                                         :toimenkuva-id (:toimenkuva-id toimenkuva)}
                                                        (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                                        {:toimenkuva "apulainen/työnjohtaja"
                                                         :nimike "Apulainen/työnjohtaja (talvikausi)"
                                                         :id (:id toimenkuva)
                                                         :toimenkuva-id (:toimenkuva-id toimenkuva)}
                                                        :else nil)

                                      toimenkuva (cond (= "päätoiminen apulainen" (:toimenkuva toimenkuva))
                                                   (assoc toimenkuva :nimike "Päätoiminen apulainen (kesäkausi)")
                                                   (= "apulainen/työnjohtaja" (:toimenkuva toimenkuva))
                                                   (assoc toimenkuva :nimike "Apulainen/työnjohtaja (kesäkausi)")
                                                   :else (merge toimenkuva {:nimike (:toimenkuva toimenkuva)}))]

                                  ;; Lisätään talvi/kesäkausi vain, jos ne on noita erikois kovakoodattuja toimenkuvia
                                  (if uusi-toimenkuva
                                    (conj uudet-toimenkuvat uusi-toimenkuva toimenkuva)
                                    (conj uudet-toimenkuvat toimenkuva))))
                        [] toimenkuvat)
                      toimenkuvat)

        ;; Urakan toimenkuviin kuuluu -2021 jälkeen aina 'valmistelukausi ennen urakka-ajan alkua'
        ;; Mutta tuota toimenkuvaa ei voida näyttää, mikäli ei ole menossa ensimmäinen hoitovuosi.
        ;; Poistetaan se tarpeen mukaan
        toimenkuvat (if (> hoitovuosi-nro 1)
                      (remove #(= "valmistelukausi ennen urakka-ajan alkua" (:toimenkuva %)) toimenkuvat)
                      toimenkuvat)

        ;; Järjestetään toimenkuvat järkevään järjestykseen
        toimenkuvat (map (fn [toimenkuva]
                           (assoc toimenkuva :jarjestys (toimenkuva-kyselyt/paattele-toimenkuvan-jarjestys (:toimenkuva toimenkuva))))
                      toimenkuvat)

        ;; Haetaan raskaalla prosessilla toimenkuvakohtaisesti suunnitellut johto-ja-hallintokorvaukset
        toimenkuvat (reduce (fn [kuvat toimenkuva]
                              (let [tarjous-rivi (first (filter #(= (:toimenkuva-id %) (:id toimenkuva)) toimenkuvat-tarjouksesta))
                                    tarjous-summa (:summa (first (filter #(= hoitovuoden-alkuvuosi (:vuosi %)) (:hoitovuosittaiset-arvot tarjous-rivi))))
                                    toimenkuva (assoc toimenkuva :tarjous-summa tarjous-summa)

                                    toimenkuvan-kuukaudet (paattele-toimenkuvan-kuukaudet urakan-alkuvuosi toimenkuva)

                                    kuukaudet (hae-toimenkuvan-johto-ja-hallintokorvaukset-kuukausittain
                                                db {:urakka-id urakka-id
                                                    :vuosi hoitovuoden-alkuvuosi
                                                    :toimenkuva-id (:id toimenkuva)
                                                    :sallitut-kuukaudet toimenkuvan-kuukaudet})

                                    ;; Jos kuukaudet on nil, niin luodaan lista default arvoilla.
                                    ;; Kuukausilistauksessa on kuitenkin valtavasti hajontaa sen perusteella, että mikä toimenkuva on kyseessä
                                    ;; Tässä on paljon historian painolastia ja kunhan vanhasta kustannusten suunnittelust apäästään kokonaan eroon,
                                    ;; niin toimenkuvat voidaan järkevöittää ja yhdenmukaistaa
                                    kuukaudet (if (seq kuukaudet)
                                                (map (fn [rivi]
                                                       (merge rivi
                                                         {:yhteensa-kk (* (if (:tuntipalkka rivi) (:tuntipalkka rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))
                                                          :yhteensa-indeksikorjattu-kk (* (if (:tuntipalkka-indeksikorjattu rivi) (:tuntipalkka-indeksikorjattu rivi) 0) (if (:tunnit rivi) (:tunnit rivi) 0))
                                                          :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                                               (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                                          :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                          :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)
                                                          :nimike (:nimike toimenkuva)}))
                                                  kuukaudet)
                                                (mapv (fn [kk]
                                                        (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                                          {:id (:id toimenkuva)
                                                           :toimenkuva (:toimenkuva toimenkuva)
                                                           :nimike (:nimike toimenkuva)
                                                           :urakka-id urakka-id
                                                           :kuukausi kk
                                                           :yhteensa-kk 0
                                                           :yhteensa-indeksikorjattu-kk nil
                                                           :vuosi vuosi
                                                           :tuntipalkka 0
                                                           :tuntipalkka-indeksikorjattu nil
                                                           :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                                           :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                           :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                                  toimenkuvan-kuukaudet))

                                    ;; Vanhoilla toimenkuvilla voi tulla vain osittaiset kuukaudet, esim talvikausi, joka on syyskuusta maaliskuuhun
                                    ;; Täytetään kuukausilista näissä tapauksissa default arvoilla
                                    kuukaudet (reduce (fn [uudet-kuukaudet kk]
                                                        (let [valittu-kuukausi (first (filter #(= (:kuukausi %) kk) kuukaudet))]
                                                          (if valittu-kuukausi
                                                            (conj uudet-kuukaudet valittu-kuukausi)
                                                            (conj uudet-kuukaudet
                                                              {:id (:id toimenkuva)
                                                               :toimenkuva (:toimenkuva toimenkuva)
                                                               :nimike (:nimike toimenkuva)
                                                               :urakka-id urakka-id
                                                               :kuukausi kk
                                                               :vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))
                                                               :yhteensa-kk 0
                                                               :yhteensa-indeksikorjattu-kk nil
                                                               :tuntipalkka 0
                                                               :tuntipalkka-indeksikorjattu nil
                                                               :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))) true)
                                                               :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                               :viimeisin-muokkaaja (:viimeisin-muokkaaja viimeisin-muokkaus)}))))
                                                [] toimenkuvan-kuukaudet)
                                    kuukaudet (vec (sort-by (juxt :vuosi :kuukausi) kuukaudet))
                                    toimenkuva (assoc toimenkuva :kuukaudet kuukaudet
                                                 :kkv (count toimenkuvan-kuukaudet))
                                    summa (apply + (map
                                                     (fn [rivi] (if (and (:tuntipalkka rivi) (:tunnit rivi))
                                                                  (* (:tuntipalkka rivi) (:tunnit rivi)) 0))
                                                     kuukaudet))
                                    summa-indeksikorjattu (apply + (map
                                                                     (fn [rivi] (if (and (:tuntipalkka-indeksikorjattu rivi) (:tunnit rivi))
                                                                                  (* (:tuntipalkka-indeksikorjattu rivi) (:tunnit rivi)) 0))
                                                                     kuukaudet))
                                    toimenkuva (assoc toimenkuva
                                                 :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                 :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)
                                                 :tuntipalkka (:tuntipalkka (first kuukaudet))
                                                 :tunnit (if (kust-domain/onko-tunnit-samat? kuukaudet) (:tunnit (first kuukaudet))
                                                           nil) ;; Aseta arvo nil, jos tunnit eivät ole samat kaikissa kuukausissa
                                                 :yhteensa-kk (* (or (:tuntipalkka (first kuukaudet)) 0) (or (:tunnit (first kuukaudet)) 0))
                                                 :yhteensa-indeksikorjattu-kk (* (or (:tuntipalkka-indeksikorjattu (first kuukaudet)) 0) (or (:tunnit (first kuukaudet)) 0))
                                                 :summa summa
                                                 :summa-indeksikorjattu summa-indeksikorjattu)]
                                (conj kuvat toimenkuva)))
                      [] toimenkuvat)

        ;; Muut kulut - kuten toimisto, ict yms on käyttöliittymässä lisätty toimenkuva -taulukkoon. Haetaan nekin
        ;; Hoidonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        muut-kulut-kuukaudet (hae-muut-kulut-toimenkuviin-kuukausittain db {:sopimus-id sopimus-id
                                                                            :vuosi hoitovuoden-alkuvuosi
                                                                            :toimenpideinstanssi-id hoidonjohto-tpi-id})
        ;; Jos muut-kulut-kuukaudet on nil, niin luodaan lista default arvoilla.
        muut-kulut-kuukaudet (if (seq muut-kulut-kuukaudet)
                               (map (fn [rivi]
                                      (merge rivi
                                        {:yhteensa-kk (if (:tuntipalkka rivi) (:tuntipalkka rivi) 0)
                                         :yhteensa-indeksikorjattu-kk (if (:tuntipalkka-indeksikorjattu rivi) (:tuntipalkka-indeksikorjattu rivi) 0)
                                         :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                              (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                         :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                         :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                 muut-kulut-kuukaudet)
                               (mapv (fn [kk]
                                       (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                         {:toimenkuva "Muut kulut"
                                          :nimike "Muut kulut"
                                          :urakka-id urakka-id
                                          :kuukausi kk
                                          :yhteensa-kk 0
                                          :yhteensa-indeksikorjattu-kk nil
                                          :vuosi vuosi
                                          :tuntipalkka 0
                                          :tuntipalkka-indeksikorjattu nil
                                          :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                          :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                          :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                 [10 11 12 1 2 3 4 5 6 7 8 9]))
        muut-kulut-kuukaudet (vec (sort-by (juxt :vuosi :kuukausi) muut-kulut-kuukaudet))

        ;; Muut kulut -rivi simuloi toimenkuvariviä.
        muu-kulu {:toimenkuva "Muut kulut"
                  :nimike "Muut kulut"
                  :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                  :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)
                  :tuntipalkka nil
                  :tunnit nil ;; muilla kuluilla ei koskaan ole oikeasti tunteja.
                  :yhteensa-kk (:yhteensa-kk (first muut-kulut-kuukaudet))
                  :yhteensa-indeksikorjattu-kk (:yhteensa-indeksikorjattu-kk (first muut-kulut-kuukaudet))
                  :summa (apply + (map (fn [rivi]
                                         (if (:yhteensa-kk rivi) (:yhteensa-kk rivi) 0))
                                    muut-kulut-kuukaudet))
                  :summa-indeksikorjattu (apply + (map (fn [rivi]
                                                         (if (:yhteensa-indeksikorjattu-kk rivi) (:yhteensa-indeksikorjattu-kk rivi) 0))
                                                    muut-kulut-kuukaudet))
                  :kuukaudet muut-kulut-kuukaudet
                  :jarjestys 99 ;; Varmistetaan, että on viimeisenä ui:lla listassa
                  }

        toimenkuvat (conj toimenkuvat muu-kulu)
        toimenkuvat (vec (sort-by :jarjestys toimenkuvat))]
    toimenkuvat))

(defn hae-johto-ja-hallintokorvaukset-2025
  "Käytetään haettaessa johto-ja-hallintokorvauksia 2025 ja myöhemmin alkaville urakoille.
   -25 vuodesta eteenpäin toimenkuvat on kustannussuunnitelmassa könttäsummana kuukausittain. Ei yksittäisinä toimenkuvina."
  [db urakka-id hoitovuoden-alkuvuosi]
  (let [johto-ja-hallintokorvaukset (hae-johto-ja-hallintokorvaukset-kuukausittain db
                                      {:urakka-id urakka-id
                                       :vuosi hoitovuoden-alkuvuosi})
        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-jjh
                                    db {:urakka-id urakka-id
                                        :vuosi hoitovuoden-alkuvuosi}))

        johto-ja-hallintokorvaukset (if (seq johto-ja-hallintokorvaukset)
                                      ;; Jos on tallennettu jo johto-ja-hallintokorvauksia, niin lisätään niihin kalenterikuukausi
                                      (mapv (fn [rivi]
                                              (merge rivi
                                                {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                                      (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                                 :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                 :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                        johto-ja-hallintokorvaukset)
                                      ;; Jos ei ole tallennettu hoidonjohtopalkkioita, niin luodaan nolla arvot
                                      (mapv (fn [kk]
                                              (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                                {:id nil
                                                 :urakka-id urakka-id
                                                 :kuukausi kk
                                                 :vuosi vuosi
                                                 :summa 0
                                                 :summa_indeksikorjattu nil
                                                 :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                                 :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                                 :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                                        [10 11 12 1 2 3 4 5 6 7 8 9]))]
    (sort-by (juxt :vuosi :kuukausi) johto-ja-hallintokorvaukset)))

(defn hae-erillishankinnat [db sopimus-id urakka-id hoitovuoden-alkuvuosi]
  (let [;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoidonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))

        viimeisin-muokkaus (first (hae-viimeisin-muokkaaja-erillishankinnoille
                                    db {:sopimus-id sopimus-id
                                        :vuosi hoitovuoden-alkuvuosi
                                        :tehtavaryhma-id (:id tehtavaryhma)
                                        :toimenpideinstanssi-id hoidonjohto-tpi-id}))

        erillishankinnat (when hoidonjohto-tpi-id
                           (hae-erillishankinta-kuukausittain db
                             {:sopimus-id sopimus-id
                              :vuosi hoitovuoden-alkuvuosi
                              :tehtavaryhma-id (:id tehtavaryhma)
                              :toimenpideinstanssi-id hoidonjohto-tpi-id}))

        erillishankinnat (if (seq erillishankinnat)
                           ;; Jos on tallennettu jo erillishankintoja, niin lisätään niihin kalenterikuukausi
                           (map (fn [rivi]
                                  (merge rivi
                                    {:kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi
                                                          (pvm/->pvm (str "01." (:kuukausi rivi) "." (:vuosi rivi))) true)
                                     :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                     :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                             erillishankinnat)
                           ;; Jos ei ole tallennettu erillishankintoja, niin luodaan nolla arvot
                           (mapv (fn [kk]
                                   (let [vuosi (if (>= kk 10) hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi))]
                                     {:id nil
                                      :sopimus sopimus-id
                                      :tehtavaryhma (:id tehtavaryhma)
                                      :toimenpideinstanssi hoidonjohto-tpi-id
                                      :kuukausi kk
                                      :vuosi vuosi
                                      :summa 0
                                      :summa_indeksikorjattu nil
                                      :kalenterikuukausi (pvm/koko-kuukausi-ja-vuosi (pvm/->pvm (str "01." kk "." vuosi)) true)
                                      :viimeisin-muokkaus (:viimeisin_muokkaus viimeisin-muokkaus)
                                      :viimeisin-muokkaaja (:viimeisin_muokkaaja viimeisin-muokkaus)}))
                             [10 11 12 1 2 3 4 5 6 7 8 9]))]
    (sort-by (juxt :vuosi :kuukausi) erillishankinnat)))

(defn kustannussuunnitelma-vahvistettu? [db urakka-id hoitovuoden-alkuvuosi]
  (let [vahvistukset (indeksikorjaukset-vahvistettu? db
                       {:urakka-id urakka-id
                        :alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
                        :loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))})
        kustannussuunnitelma-vahvistettu? (every? true? (flatten (map vals vahvistukset)))]
    kustannussuunnitelma-vahvistettu?))


(defn paivita-tavoite-ja-kattohinta
  "Lasketaan tarjouksen, sekä muutosten perusteella.
  = tarjous + aiempien vuosien pysyvät muutokset
  Hankintakustannukset eivät vaikuta tähän laskentaan."
  [db kayttaja-id urakka-id hoitovuoden-alkuvuosi aiempien-vuosien-pysyvat-muutokset]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitokausinumero (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
        urakan-parametrit (first (urakat-q/hae-urakan-parametrit db {:urakkaid urakka-id}))
        urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)

        ;; Lasketaan indeksikorjaamaton pysyvien muutosten määrä, indeksikorjattu saatavilla :tavoitehinnan-muutos-indeksikorjattu
        pysyvat-muutokset-maara (reduce + (map :tavoitehinnan-muutos aiempien-vuosien-pysyvat-muutokset))

        kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)

        tarjous-data (hae-urakan-hoitovuoden-tarjous db
                       {:urakka_id urakka-id
                        :hoitokauden_alkuvuosi hoitovuoden-alkuvuosi})

        tarjous (-> tarjous-data first :tarjous_tavoitehinta)

        hoitovuoden-alun-tavoitehinta (+ (or tarjous 0M) (or pysyvat-muutokset-maara 0M))

        hoitovuoden-alun-kattohinta (or (when kattohintakerroin
                                          (* kattohintakerroin hoitovuoden-alun-tavoitehinta)) 0)

        tavoitetiedot (first (hae-urakan-hoitovuoden-tavoitetiedot db {:hoitokausinumero hoitokausinumero
                                                                       :urakka-id urakka-id}))

        ;; Päivitä tai lisää tavoite ja kattohinta tietokantaan
        tiedot (if (:id tavoitetiedot)
                 (paivita-tavoite-ja-kattohinta<! db
                   {:urakka-id urakka-id
                    :hoitokausinumero hoitokausinumero
                    :tavoitehinta hoitovuoden-alun-tavoitehinta
                    :tavoitehinta_indeksikorjattu (laske-indeksikorjattu-summa hoitovuoden-alun-tavoitehinta urakan-indeksit hoitokausinumero)
                    :kattohinta hoitovuoden-alun-kattohinta
                    :kattohinta_indeksikorjattu (laske-indeksikorjattu-summa hoitovuoden-alun-kattohinta urakan-indeksit hoitokausinumero)
                    :muokkaaja kayttaja-id})
                 (lisaa-tavoite-ja-kattohinta<! db
                   {:urakka-id urakka-id
                    :hoitokausinumero hoitokausinumero
                    :tavoitehinta hoitovuoden-alun-tavoitehinta
                    :tavoitehinta_indeksikorjattu (laske-indeksikorjattu-summa hoitovuoden-alun-tavoitehinta urakan-indeksit hoitokausinumero)
                    :kattohinta hoitovuoden-alun-kattohinta
                    :kattohinta_indeksikorjattu (laske-indeksikorjattu-summa hoitovuoden-alun-kattohinta urakan-indeksit hoitokausinumero)
                    :luoja kayttaja-id}))]
    tiedot))

(defn tallenna-hankintojen-kuukausittainen-summa [db kk-jakso alkujakso? viimeinen-summa summa hoitovuoden-alkuvuosi sopimus-id
                                                  toimenpideinstanssi-id kayttaja-id urakan-indeksit hoitovuosi-nro]
  (let [_ (doseq [kk kk-jakso]
            (let [summa (cond
                          (and alkujakso? (= kk 12)) viimeinen-summa
                          (and (not alkujakso?) (= kk 9)) viimeinen-summa
                          :else summa)
                  dbrivi (first (hae-kiintea-kustannus-toimenpiteelle-kuukaudelta db
                                  {:vuosi hoitovuoden-alkuvuosi
                                   :kuukausi kk
                                   :sopimus-id sopimus-id
                                   :toimenpideinstanssi-id toimenpideinstanssi-id}))
                  _ (if (:id dbrivi)
                      (paivita-kiinteat-kustannukset-kuukausittain<! db
                        {:id (:id dbrivi)
                         :vuosi hoitovuoden-alkuvuosi
                         :kuukausi kk
                         :summa summa
                         :summa_indeksikorjattu (laske-indeksikorjattu-summa summa urakan-indeksit hoitovuosi-nro)
                         :toimenpideinstanssi-id toimenpideinstanssi-id
                         :tehtavaryhma nil
                         :tehtava nil
                         :muokkaaja kayttaja-id})
                      ;; Lisää uusi
                      (tallenna-kiinteat-kustannukset-kuukaudelta<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id toimenpideinstanssi-id
                         :vuosi hoitovuoden-alkuvuosi
                         :kuukausi kk
                         :summa summa
                         :summa_indeksikorjattu (laske-indeksikorjattu-summa summa urakan-indeksit hoitovuosi-nro)
                         :tehtavaryhma nil
                         :tehtava nil
                         :luoja kayttaja-id}))]))]))

(defn tallenna-kilpailutettavat-hankinnat
  [db kayttaja urakka-id hoitovuoden-alkuvuosi kilpailutettavat-hankinnat]
  ;; Lisätään transktiot, jottei yhden epäonnistuminen päästä muita läpi
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
        hoitovuosi-nro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/->pvm (str "01.11." hoitovuoden-alkuvuosi)))
        ; Splittaa alkukauden summat kuukausittain
        _ (doseq [{:keys [alkukausi loppukausi toimenpideinstanssi-id]} kilpailutettavat-hankinnat]
            (let [alkukausi (bigdec alkukausi)

                  alkukausi-kuukausisumma (yleiset/round2 2 (with-precision 4 (/ alkukausi 3)))
                  alkukausi-viimeinen-kuukausi (- alkukausi (* 2 alkukausi-kuukausisumma))
                  loppukausi (bigdec loppukausi)
                  loppukausi-kuukausisumma (yleiset/round2 2 (with-precision 4 (/ loppukausi 9)))
                  loppukausi-viimeinen-kuukausi (- loppukausi (* 8 loppukausi-kuukausisumma))
                  ;; Tallenna alkujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 10 13) true alkukausi-viimeinen-kuukausi alkukausi-kuukausisumma
                      hoitovuoden-alkuvuosi sopimus-id toimenpideinstanssi-id (:id kayttaja) urakan-indeksit hoitovuosi-nro)
                  ;; Tallenna loppujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 1 10) false loppukausi-viimeinen-kuukausi loppukausi-kuukausisumma
                      (inc hoitovuoden-alkuvuosi) sopimus-id toimenpideinstanssi-id (:id kayttaja) urakan-indeksit hoitovuosi-nro)]))]))

(defn tallenna-erillishankinnat
  [db kayttaja urakka-id erillishankinnat hoitovuoden-alkuvuosi]
  (let [urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitovuosi-nro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/->pvm (str "01.11." hoitovuoden-alkuvuosi)))
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoidonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db
                              {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))

        _ (doseq [rivi erillishankinnat]
            (let [vuosi (if (< (:kuukausi rivi) 10) (inc hoitovuoden-alkuvuosi) hoitovuoden-alkuvuosi)
                  dbrivi (first (hae-tallennetun-kuukauden-erillishankinta db {:sopimus-id sopimus-id
                                                                               :toimenpideinstanssi-id hoidonjohto-tpi-id
                                                                               :vuosi vuosi
                                                                               :kuukausi (:kuukausi rivi)
                                                                               :tehtavaryhma-id (:id tehtavaryhma)}))
                  _ (if (:id dbrivi)
                      (paivita-kuukauden-erillishankinta<! db
                        {:id (:id dbrivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu (laske-indeksikorjattu-summa (:summa rivi) urakan-indeksit hoitovuosi-nro)
                         :muokkaaja (:id kayttaja)})
                      ;; Lisää uusi
                      (tallenna-kuukauden-erillishankinta<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id hoidonjohto-tpi-id
                         :vuosi vuosi
                         :kuukausi (:kuukausi rivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu (laske-indeksikorjattu-summa (:summa rivi) urakan-indeksit hoitovuosi-nro)
                         :tehtavaryhma-id (:id tehtavaryhma)
                         :luoja (:id kayttaja)}))]))]))

(defn tallenna-kuukausittaiset-muut-kulut [db kuukaudet urakan-indeksit kayttaja sopimus-id
                                           toimenpideinstanssi-id hoitovuosi-nro hoitovuoden-alkuvuosi]
  (let [tehtava (hae-tehtava-tunnisteella db {:tunniste "8376d9c4-3daf-4815-973d-cd95ca3bb388"})
        tehtava-id (:id (first tehtava))] ;; Muut kulut tehtävä
    (doseq [{:keys [kuukausi yhteensa-kk] :as rivi} (sort-by (juxt :vuosi :kuukausi) kuukaudet)]
      (let [vuosi (if (< kuukausi 10) (inc hoitovuoden-alkuvuosi) hoitovuoden-alkuvuosi)
            ;; Haetaan muut-kulut kuukaudelle
            db-kuukausi (first (hae-muut-kulut-kuukaudelle db {:sopimus-id sopimus-id
                                                               :toimenpideinstanssi-id toimenpideinstanssi-id
                                                               :vuosi vuosi
                                                               :kuukausi kuukausi}))
            t (if-not db-kuukausi
                (lisaa-kuukauden-muu-kulu<! db
                  {:summa yhteensa-kk
                   :summa_indeksikorjattu (laske-indeksikorjattu-summa yhteensa-kk urakan-indeksit hoitovuosi-nro)
                   :vuosi vuosi
                   :kuukausi kuukausi
                   :toimenpideinstanssi-id toimenpideinstanssi-id
                   :tehtava-id tehtava-id
                   :sopimus-id sopimus-id
                   :luoja (:id kayttaja)})
                (paivita-kuukauden-muu-kulu<! db
                  {:id (:id db-kuukausi)
                   :summa yhteensa-kk
                   :summa_indeksikorjattu (laske-indeksikorjattu-summa yhteensa-kk urakan-indeksit hoitovuosi-nro)
                   :muokkaaja (:id kayttaja)}))]))))

(defn tallenna-kuukausittaiset-toimenkuvat [db kuukaudet urakan-indeksit urakan-tiedot kayttaja urakka-id
                                            toimenkuva-id hoitovuosi-nro hoitovuoden-alkuvuosi]
  (let [urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))]
    (doseq [{:keys [kuukausi tunnit tuntipalkka] :as rivi} (sort-by (juxt :vuosi :kuukausi) kuukaudet)]
      (let [vuosi (if (< kuukausi 10) (inc hoitovuoden-alkuvuosi) hoitovuoden-alkuvuosi)
            ;; Haetaan toimenkuvan kuukauden johto-ja-hallintokorvaus
            db-kuukausi (first (hae-toimenkuvan-kuukauden-johto-ja-hallintokorvaus db {:urakka-id urakka-id
                                                                                       :toimenkuva-id toimenkuva-id
                                                                                       :vuosi vuosi
                                                                                       :kuukausi kuukausi}))
            tunnit (if (>= urakan-alkuvuosi 2022)
                     1 ;; Kaikissa -22 tai myöhemmin alkaneissa urakoissa käytetään kokonaishintaa. Yksittäistä tuntia ei enää tallenneta
                     tunnit)

            t (if-not db-kuukausi
                (lisaa-kuukauden-johto-ja-hallintokorvaus<! db
                  {:urakka-id urakka-id
                   :toimenkuva-id toimenkuva-id
                   :vuosi vuosi
                   :kuukausi kuukausi
                   :tunnit tunnit
                   :tuntipalkka tuntipalkka
                   :tuntipalkka_indeksikorjattu (laske-indeksikorjattu-summa tuntipalkka urakan-indeksit hoitovuosi-nro)
                   :luoja (:id kayttaja)})
                (paivita-kuukauden-johto-ja-hallintokorvaus<! db
                  {:id (:id db-kuukausi)
                   :tuntipalkka tuntipalkka
                   :tunnit tunnit
                   :tuntipalkka_indeksikorjattu (laske-indeksikorjattu-summa tuntipalkka urakan-indeksit hoitovuosi-nro)
                   :muokkaaja (:id kayttaja)}))]))))

(defn tallenna-vuosittaiset-toimenkuvat [db rivi urakan-indeksit kayttaja urakka-id toimenkuva-id hoitovuosi-nro hoitovuoden-alkuvuosi]
  (let [vuosi (if (< (:kuukausi rivi) 10) (inc hoitovuoden-alkuvuosi) hoitovuoden-alkuvuosi)
        dbrivi (first (hae-tallennettu-kuukauden-johto-ja-hallintokorvaus db {:urakka-id urakka-id
                                                                              :toimenkuva-id toimenkuva-id
                                                                              :vuosi vuosi
                                                                              :kuukausi (:kuukausi rivi)}))
        _ (if (:id dbrivi)
            (paivita-kuukauden-johto-ja-hallintokorvaus<! db
              {:id (:id dbrivi)
               :tuntipalkka (:summa rivi)
               :tunnit 1
               :tuntipalkka_indeksikorjattu (laske-indeksikorjattu-summa (:summa rivi) urakan-indeksit hoitovuosi-nro)
               :muokkaaja (:id kayttaja)})
            ;; Lisää uusi
            (lisaa-kuukauden-johto-ja-hallintokorvaus<! db
              {:urakka-id urakka-id
               :toimenkuva-id toimenkuva-id
               :vuosi vuosi
               :kuukausi (:kuukausi rivi)
               :tunnit 1
               :tuntipalkka (:summa rivi)
               :tuntipalkka_indeksikorjattu (laske-indeksikorjattu-summa (:summa rivi) urakan-indeksit hoitovuosi-nro)
               :luoja (:id kayttaja)}))]))

(defn tallenna-johto-ja-hallintokorvaukset
  [db kayttaja urakka-id johto-ja-hallintokorvaukset hoitovuoden-alkuvuosi]
  (let [;; Johto-ja hallintakorvausten mukana tulee -19 - 24 alkavilla urakoilla myös "Muut kulut" rivi
        ;; Poistetaan se tarvittaessa listasta ja tallennetaan erikseen
        vain-jjh (filter (fn [rivi] (not= "Muut kulut" (:toimenkuva rivi))) johto-ja-hallintokorvaukset)
        muut-kulut (first (filter (fn [rivi] (= "Muut kulut" (:toimenkuva rivi))) johto-ja-hallintokorvaukset))

        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
        hoitovuosi-nro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/->pvm (str "01.11." hoitovuoden-alkuvuosi)))

        toimenpideinstanssi-id (:id (first
                                      (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                        {:urakka urakka-id
                                         :koodi (mhu/toimenpide-avain->toimenpide :mhu-johto)})))

        ; Tallenna kuukausittaiset summat
        _ (doseq [toimenkuva vain-jjh]
            (let [;; rivi voi sisältää joko kuukausisumman kaikille toimenkuville (silloin id 1) tai
                  ;; toimenkuvan, jolla on kuukausittaiset arvot, tunnit ja tuntipalkat.
                  kuukaudet (when (<= urakan-alkuvuosi 2024) (:kuukaudet toimenkuva))

                  ;; Toimenkuva on tietokannassa pakollinen.
                  ;; Asetetaan jokin toimenkuva myös 2025-> urakoille, koska oikeaa toimenkuvaa ei voida uudessa kustannusten suunnittelussa asettaa.
                  ;; Kuukausittaiset yhteenvetorivit eivät ole riippuvaisia toimenkuvasta, joten voidaan käyttää mitä tahansa.
                  toimenkuva-id (if (>= urakan-alkuvuosi 2025) 1 (:id toimenkuva))

                  _ (if (<= urakan-alkuvuosi 2024)
                      (tallenna-kuukausittaiset-toimenkuvat db kuukaudet urakan-indeksit urakan-tiedot kayttaja
                        urakka-id toimenkuva-id hoitovuosi-nro hoitovuoden-alkuvuosi)
                      (tallenna-vuosittaiset-toimenkuvat db toimenkuva urakan-indeksit kayttaja urakka-id
                        toimenkuva-id hoitovuosi-nro hoitovuoden-alkuvuosi))]))

        ;; Tallennetaan mahdolliset muut kulut
        _ (when muut-kulut
            (tallenna-kuukausittaiset-muut-kulut db (:kuukaudet muut-kulut) urakan-indeksit kayttaja
              sopimus-id toimenpideinstanssi-id hoitovuosi-nro hoitovuoden-alkuvuosi))

        _ (ka-q/merkitse-kustannussuunnitelmat-likaisiksi! db {:toimenpideinstanssi toimenpideinstanssi-id})
        _ (kiint-kyselyt/merkitse-maksuerat-likaisiksi-hoidonjohdossa! db {:toimenpideinstanssi toimenpideinstanssi-id})]))

(defn tallenna-hoidonjohtopalkkiot
  [db kayttaja urakka-id hoidonjohtopalkkiot hoitovuoden-alkuvuosi]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitovuosi-nro (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/->pvm (str "01.11." hoitovuoden-alkuvuosi)))
        urakan-indeksit (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id)
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoidonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))

        ; Tallenna kuukausittaiset summat
        _ (doseq [rivi hoidonjohtopalkkiot]
            (let [summa (:summa rivi)
                  kuukausi (:kuukausi rivi)
                  vuosi (if (< kuukausi 10) (inc hoitovuoden-alkuvuosi) hoitovuoden-alkuvuosi)
                  dbrivi (first
                           (hae-olemassa-oleva-hoidonjohtopalkkio db {:sopimus-id sopimus-id
                                                                      :toimenpideinstanssi-id hoidonjohto-tpi-id
                                                                      :vuosi vuosi
                                                                      :kuukausi kuukausi
                                                                      :tehtava-id (:id tehtava)}))
                  id (:id dbrivi)
                  _ (if id
                      (paivita-kuukauden-hoidonjohtopalkkio<! db
                        {:id id
                         :summa summa
                         :summa_indeksikorjattu (laske-indeksikorjattu-summa summa urakan-indeksit hoitovuosi-nro)
                         :muokkaaja (:id kayttaja)})
                      ;; Lisää uusi
                      (tallenna-kuukauden-hoidonjohtopalkkio<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id hoidonjohto-tpi-id
                         :vuosi vuosi
                         :kuukausi kuukausi
                         :summa summa
                         :summa_indeksikorjattu (laske-indeksikorjattu-summa summa urakan-indeksit hoitovuosi-nro)
                         :tehtava-id (:id tehtava)
                         :luoja (:id kayttaja)}))]))]))

(defn puuttuvat-suunnitelmat
  "Jotta urakan tavoitehinta voidaan vahvistaa, Kustannusten Suunnittelussa pitää olla täydennettynä kaikki tiedot ja niiden
  täytyy vastata samoja arvoja, mitä tarjouksessa on annettu."
  [db urakka-id hoitovuoden-alkuvuosi hoitovuoden-tarjous aiempien-vuosien-pysyvat-muutokset]
  (let [urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        puuttuvat []
        ;; Kaikki kustannussuunnitelman summat vaikuttaa tavoitehintaan
        ;; Pysyvät muutokset lisätään mukaan joko vähentämään tai lisäämään tavoitehintaa
        kilpailutettavat-hankinnat (hae-kiinteat-kustannukset db sopimus-id urakka-id hoitovuoden-alkuvuosi)

        kilpailutettavat-hankinnat-yht (apply + (map #(or (:yhteensa %) 0) kilpailutettavat-hankinnat))
        tarjous-hankinnat (filter (fn [rivi] (= "hankintakustannukset" (:osio rivi))) (:kustannukset hoitovuoden-tarjous))
        tarjous-hankinnat-yht (:summa (first tarjous-hankinnat))

        kilpailutettavat-hankinnat-yht (bigdec (or kilpailutettavat-hankinnat-yht 0.0))
        tarjous-hankinnat-yht (bigdec (or tarjous-hankinnat-yht 0.0))

        pysyvat-muutokset-maara (reduce + (map :tavoitehinnan-muutos aiempien-vuosien-pysyvat-muutokset))
        pysyvat-muutokset-maara (bigdec (or pysyvat-muutokset-maara 0.0))

        virhe-teksti (if (>= urakan-alkuvuosi 2025)
                       "“Kilpailutettavat hankinnat”-osiossa erittelyt eivät täsmää tarjouksen kanssa."
                       "“Kilpailutettavat hankinnat” puuttuu.")

        puuttuvat (cond
                    ;; Tarkistetaan, että hankinnat osio = tarjouksen hankinnat + pysyvät muutokset 
                    (and
                      (>= urakan-alkuvuosi 2025)
                      (boolean (seq kilpailutettavat-hankinnat))
                      (= kilpailutettavat-hankinnat-yht (+
                                                          (or tarjous-hankinnat-yht 0)
                                                          (or pysyvat-muutokset-maara 0))))
                    puuttuvat
                    ;; Tarkistetaan, että hankinnat osio ei ole 0 2024 tai aiemmin alkaneilla
                    (and
                      (<= urakan-alkuvuosi 2024)
                      (boolean (seq kilpailutettavat-hankinnat))
                      (> (apply + (keep #(:yhteensa %) kilpailutettavat-hankinnat)) 0M))
                    puuttuvat
                    :else (conj puuttuvat virhe-teksti))

        erillishankinnat (hae-erillishankinnat db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        erillishankinnat-yht (apply + (keep #(:summa %) erillishankinnat))
        tarjous-erillishankinnat (filter (fn [rivi] (= "erillishankinnat" (:osio rivi))) (:kustannukset hoitovuoden-tarjous))
        tarjous-erillishankinnat-yht (:summa (first tarjous-erillishankinnat))

        tarjous-erillishankinnat-yht (bigdec (or tarjous-erillishankinnat-yht 0.0))
        erillishankinnat-yht (bigdec (or erillishankinnat-yht 0.0))

        virhe-teksti (if (>= urakan-alkuvuosi 2025)
                       "”Erillishankinnat”-osiossa erittelyt eivät täsmää tarjouksen kanssa."
                       "”Erillishankinnat” puuttuu")

        puuttuvat (cond
                    ;; Tarkistetaan, että hankinnat osio = tarjouksen erillishankinnat
                    (and (>= urakan-alkuvuosi 2025) (boolean (seq erillishankinnat)) (= erillishankinnat-yht tarjous-erillishankinnat-yht))
                    puuttuvat
                    ;; Tarkistetaan, että hankinnat osio ei ole 0 2024 tai aiemmin alkaneilla
                    (and (<= urakan-alkuvuosi 2024) (boolean (seq erillishankinnat)) (some #(and
                                                                                              (:summa %)
                                                                                              (not= (bigdec (:summa %)) 0M)) erillishankinnat))
                    puuttuvat
                    :else (conj puuttuvat virhe-teksti))

        hoidonjohtopalkkiot (hae-hoidonjohtopalkkiot db sopimus-id urakka-id hoitovuoden-alkuvuosi)
        hoidonjohtopalkkiot-yht (apply + (map #(or (:summa %) 0) hoidonjohtopalkkiot))
        tarjous-hoidonjohtopalkkiot (filter (fn [rivi] (= "hoidonjohtopalkkio" (:osio rivi))) (:kustannukset hoitovuoden-tarjous))
        tarjous-hoidonjohtopalkkiot-yht (:summa (first tarjous-hoidonjohtopalkkiot))

        tarjous-hoidonjohtopalkkiot-yht (bigdec (or tarjous-hoidonjohtopalkkiot-yht 0.0))
        hoidonjohtopalkkiot-yht (bigdec (or hoidonjohtopalkkiot-yht 0.0))

        virhe-teksti (if (>= urakan-alkuvuosi 2025)
                       "”Hoidonjohtopalkkio”-osiossa erittelyt eivät täsmää tarjouksen kanssa."
                       "”Hoidonjohtopalkkio” puuttuu.")

        puuttuvat (cond
                    ;; Tarkistetaan, että hoidonjohtopalkkio osio = tarjouksen hoidonjohtopalkkiot
                    (and (>= urakan-alkuvuosi 2025) (boolean (seq hoidonjohtopalkkiot)) (= hoidonjohtopalkkiot-yht tarjous-hoidonjohtopalkkiot-yht))
                    puuttuvat
                    ;; Tarkistetaan, että hoidonjohtopalkkio osio ei ole 0
                    (and (<= urakan-alkuvuosi 2024) (boolean (seq hoidonjohtopalkkiot)) (some #(and
                                                                                                 (:summa %)
                                                                                                 (not= (bigdec (:summa %)) 0M)) hoidonjohtopalkkiot))
                    puuttuvat
                    :else (conj puuttuvat virhe-teksti))

        johto-ja-hallintokorvaukset (cond
                                      (and (>= urakan-alkuvuosi 2019) (<= urakan-alkuvuosi 2024))
                                      (hae-johto-ja-hallintokorvaukset-2019-2024 db urakka-id sopimus-id hoitovuoden-alkuvuosi urakan-alkuvuosi nil)
                                      (>= urakan-alkuvuosi 2025)
                                      (hae-johto-ja-hallintokorvaukset-2025 db urakka-id hoitovuoden-alkuvuosi)
                                      :else (hae-johto-ja-hallintokorvaukset-2025 db urakka-id hoitovuoden-alkuvuosi))
        johto-ja-hallintokorvaukset-yht (if (<= urakan-alkuvuosi 2024)
                                          (round2 2 (apply + (map #(or (:yhteensa-kk %) 0.0M)
                                                               (mapcat :kuukaudet johto-ja-hallintokorvaukset))))
                                          (apply + (map #(or (:summa %) 0) johto-ja-hallintokorvaukset)))


        tarjous-jjh (filter (fn [rivi] (= "johto-ja-hallintokorvaus" (:osio rivi))) (:toimenkuvat hoitovuoden-tarjous))
        tarjous-jjh-yht (apply + (map #(or (:summa %) 0.0M) tarjous-jjh))

        ;; Tarkistetaan että jotain on kirjattu jjh osioon
        jjh-summia-olemassa? (cond
                               ;; Ennen 25 urakoilla oma tietomallinsa
                               (<= urakan-alkuvuosi 2024)
                               (boolean
                                 (some #(pos? (or (:tuntipalkka %) 0))
                                   (mapcat :kuukaudet johto-ja-hallintokorvaukset)))

                               ;; 25 sekä jälkeen oma tietomallinsa
                               :else
                               (boolean
                                 (some
                                   #(not (zero? (or (:summa %) 0))) johto-ja-hallintokorvaukset)))

        johto-ja-hallintokorvaukset-yht (bigdec (or johto-ja-hallintokorvaukset-yht 0.0))
        tarjous-jjh-yht (bigdec (or tarjous-jjh-yht 0.0))

        virhe-teksti (if (>= urakan-alkuvuosi 2025)
                       "”Johto-ja-hallintokorvaukset”-osiossa erittelyt eivät täsmää tarjouksen kanssa."
                       "”Johto-ja-hallintokorvaukset” puuttuu")

        puuttuvat (cond
                    ;; 2025 ja myöhemmin alkavilla urakoilla summien tulee täsmätä tarjouksen kanssa
                    (and (>= urakan-alkuvuosi 2025) (= johto-ja-hallintokorvaukset-yht tarjous-jjh-yht))
                    puuttuvat
                    (and (<= urakan-alkuvuosi 2024) jjh-summia-olemassa?)
                    puuttuvat
                    :else (conj puuttuvat virhe-teksti))]
    puuttuvat))

(defn paivita-kustannussuunnitelman-tila [db vahvistetut-osiot vahvista? hoitovuoden-nro urakka-id osio kayttaja-id]
  (let [osio-olemassa (first (filter #(= (name osio) (:osio %)) vahvistetut-osiot))]
    (if-not osio-olemassa
      (lisaa-kustannussuunnitelma-osio db {:urakkaid urakka-id
                                           :hoitovuosi hoitovuoden-nro
                                           :osio osio
                                           :luoja kayttaja-id
                                           :vahvistaja (if vahvista? kayttaja-id nil)
                                           :vahvistettu vahvista?
                                           :vahvistus_pvm (if vahvista? (pvm/nyt) nil)})
      (paivita-kustannussuunnitelma-osio db {:id (:id osio-olemassa)
                                             :urakkaid urakka-id
                                             :hoitovuosinro hoitovuoden-nro
                                             :osio osio
                                             :muokkaaja kayttaja-id
                                             :vahvistettu vahvista?
                                             :vahvistaja (if vahvista? kayttaja-id nil)
                                             :vahvistus_pvm (if vahvista? (pvm/nyt) nil)}))))

(defn paivita-kasin-syotetty-kattohinta [db kayttaja-id urakka-id hoitovuoden-alkuvuosi paivitetty-kattohinta
                                         urakan-indeksit kustannussuunnitelma urakan-parametrit]
  (let [urakan-tiedot (first (urakat-q/hae-urakan-tiedot db urakka-id))
        ;; Varmista ensin, että annettu käsin syötetty kattohinta on suurempi kuin hoitovuoden alun tavoitehinta
        hoitovuoden-alun-tavoitehinta (or (get-in kustannussuunnitelma [:kustannussuunnitelma :hoitovuoden-alun-tavoitehinta]) 0)
        _ (when (and paivitetty-kattohinta (< paivitetty-kattohinta hoitovuoden-alun-tavoitehinta))
            (throw (IllegalArgumentException. (str "Annettu kattohinta " paivitetty-kattohinta " on pienempi, kuin hoitovuoden alun tavoitehinta: " hoitovuoden-alun-tavoitehinta))))

        hoitovuosinro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
        ;; Riipumatta vahvistuksen onnistumisesta, aseta kattohinta, jos se on annettu
        kattohinta-indeksikorjattu (laske-indeksikorjattu-summa paivitetty-kattohinta urakan-indeksit hoitovuosinro)
        ;; Hae nykyinen kattohinta
        nykyiset-kattohinnat (first (hae-urakan-hoitovuoden-tavoitetiedot db {:hoitokausinumero hoitovuosinro
                                                                              :urakka-id urakka-id}))

        ;; Päivitä kattohinta, jos se on annettu
        _ (when (and paivitetty-kattohinta (:muokkaa_kattohinta_kasin urakan-parametrit))
            (if nykyiset-kattohinnat
              (paivita-kasin-syotetty-kattohinta! db {:muokkaaja kayttaja-id
                                                      :urakka-id urakka-id
                                                      :hoitovuosinro hoitovuosinro
                                                      :kattohinta paivitetty-kattohinta
                                                      :kattohinta-indeksikorjattu kattohinta-indeksikorjattu})
              (aseta-kasin-syotetty-kattohinta<! db {:luoja kayttaja-id
                                                     :urakka-id urakka-id
                                                     :hoitovuosinro hoitovuosinro
                                                     :kattohinta paivitetty-kattohinta
                                                     :kattohinta-indeksikorjattu kattohinta-indeksikorjattu})))]))

(defn vahvista-tavoite-ja-kattohinta [db kayttaja urakka-id vahvista? hoitovuoden-alkuvuosi]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        hoitovuosinro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) hoitovuoden-alkuvuosi)
        vahvistus-pvm (pvm/nyt)
        hoitokauden-alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
        hoitokauden-loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))
        vahvistetut-osiot (hae-kustannussuunnitelman-osiot db {:urakkaid urakka-id :hoitovuosinro hoitovuosinro})
        ;; Haetaan budjettitavoite
        budjettitavoitteet (budjettisuunnittelu-q/budjettitavoite-vuodelle db urakka-id hoitovuoden-alkuvuosi)
        ;; Haetaan tavoitehinta
        hoitovuoden-indeksikorjattu-tavoitehinta (:tavoitehinta-indeksikorjattu budjettitavoitteet)
        laskutusrajan-tarkistukset (hae-laskutusrajan-tarkistukset
                                     db {:urakka urakka-id
                                         :hoitokauden_alkuvuosi hoitovuoden-alkuvuosi
                                         :hoitovuoden_indeksikorjattu_tavoitehinta hoitovuoden-indeksikorjattu-tavoitehinta})
        laskutusrajan-tarkistus (or (:laskutusrajan-tarkistus (last laskutusrajan-tarkistukset)) 0)

        ;; Vanhassa kustannussuunnitelmassa oli erilliset osiot eri kustannuslajeille. Uudessa ei ole, mutta tehdään näin
        ;; jotta taaksepäin yhteensopivuus säilyy.

        ;; Lisää hankintakustannusosiotieto kantaan
        _ (paivita-kustannussuunnitelman-tila db vahvistetut-osiot vahvista? hoitovuosinro urakka-id "hankintakustannukset" (:id kayttaja))
        ;; Lisää erillishankinnat tieto kantaan
        _ (paivita-kustannussuunnitelman-tila db vahvistetut-osiot vahvista? hoitovuosinro urakka-id "erillishankinnat" (:id kayttaja))
        ;; Lisää hoidonjohtopalkkio tieto kantaan
        _ (paivita-kustannussuunnitelman-tila db vahvistetut-osiot vahvista? hoitovuosinro urakka-id "hoidonjohtopalkkio" (:id kayttaja))
        ;; Lisää rahavarausosio tieto kantaan
        _ (paivita-kustannussuunnitelman-tila db vahvistetut-osiot vahvista? hoitovuosinro urakka-id "tilaajan-rahavaraukset" (:id kayttaja))
        ;; Lisää tavoitehintaiset-rahavaraukset tieto kantaan
        _ (paivita-kustannussuunnitelman-tila db vahvistetut-osiot vahvista? hoitovuosinro urakka-id "tavoitehintaiset-rahavaraukset" (:id kayttaja))
        ;; Lisää johto-ja-hallintokorvaus tieto kantaan
        _ (paivita-kustannussuunnitelman-tila db vahvistetut-osiot vahvista? hoitovuosinro urakka-id "johto-ja-hallintokorvaus" (:id kayttaja))
        ;; Lisää tavoite-ja-kattohinta tieto kantaan
        _ (paivita-kustannussuunnitelman-tila db vahvistetut-osiot vahvista? hoitovuosinro urakka-id "tavoite-ja-kattohinta" (:id kayttaja))

        ;; Vahvista kiinteähintaiset työt.
        _ (vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille! db
            {:urakka-id urakka-id
             :alkupvm hoitokauden-alkupvm
             :loppupvm hoitokauden-loppupvm
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm})

        ;; Vahvista kustannusarvioidut työt.
        _ (vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille! db
            {:urakka-id urakka-id
             :alkupvm hoitokauden-alkupvm
             :loppupvm hoitokauden-loppupvm
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm})

        ;; Vahvista johto-ja-hallintokorvaukset.
        _ (vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille! db
            {:urakka-id urakka-id
             :alkupvm hoitokauden-alkupvm
             :loppupvm hoitokauden-loppupvm
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm})

        _ (vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille! db
            {:urakka-id urakka-id
             :vuosi hoitovuoden-alkuvuosi
             :hoitovuosi-nro hoitovuosinro
             :vahvista? vahvista?
             :vahvistaja (:id kayttaja)
             :vahvistus-pvm vahvistus-pvm
             :laskutusrajan-tarkistus-summa laskutusrajan-tarkistus})]))

(defn onko-tulevilla-hoitovuosilla-arvoja? [db urakka-id sopimus-id hoitovuoden-alkuvuosi urakan-loppuvuosi]
  (let [hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        erillishankinnat-tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))
        hankinnan-toimenpiteet (flatten (map (juxt :toimenpideinstanssi-id) (hae-urakan-toimenpiteet db {:urakkaid urakka-id})))
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))

        ;; Ei haittaa, jos loppuvuosi valittuna, palauttaa silloin vaan false
        alkupvm (str (inc hoitovuoden-alkuvuosi) "-10-01")
        loppupvm (str urakan-loppuvuosi "-09-30")
        tulevaisuudessa-arvoja (tulevilla-hoitovuosilla-arvoja? db
                                 {:urakka-id urakka-id
                                  :alkupvm alkupvm
                                  :loppupvm loppupvm
                                  :sopimus-id sopimus-id
                                  :tehtava-id (:id tehtava)
                                  :hoidon-johdon-tpi-id hoidonjohto-tpi-id
                                  :hankinnan-toimenpideinstanssit hankinnan-toimenpiteet
                                  :erillishankinnat-tehtavaryhma-id (:id erillishankinnat-tehtavaryhma)})]
    tulevaisuudessa-arvoja))
