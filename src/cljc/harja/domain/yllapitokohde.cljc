(ns harja.domain.yllapitokohde
  "Ylläpitokohteiden yhteisiä apureita"
  (:require
    [harja.tyokalut.spec-apurit :as spec-apurit]
    [clojure.string :as str]
    [harja.domain.tierekisteri :as tr-domain]
    [clojure.spec.alpha :as s]
    [harja.pvm :as pvm]
    #?@(:clj
        [[harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]

         [harja.pvm :as pvm]
         [clj-time.core :as t]
         [taoensso.timbre :as log]
         [clj-time.coerce :as c]])))

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::kohdenumero (s/nilable string?))
(s/def ::nimi string?)
(s/def ::kokonaishinta (s/and number?))

(def ^{:doc "Sisältää vain nykyisin käytössä olevat luokat 1,2 ja 3 (eli numerot 8, 9 ja 10)."}
nykyiset-yllapitoluokat
  [{:lyhyt-nimi "1" :nimi "Luokka 1" :numero 8}
   {:lyhyt-nimi "2" :nimi "Luokka 2" :numero 9}
   {:lyhyt-nimi "3" :nimi "Luokka 3" :numero 10}
   {:lyhyt-nimi "-" :nimi "Ei ylläpitoluokkaa" :numero nil}])

(def vanhat-yllapitoluokat ^{:doc "Sisältää vanhat ylläpitoluokat, tarvitaan YHA:n kanssa taakseepäinyhteensopivuuden vuoksi."}
[{:lyhyt-nimi "1a" :nimi "Luokka 1a" :numero 1}
 {:lyhyt-nimi "1b" :nimi "Luokka 1b" :numero 2}
 {:lyhyt-nimi "1c" :nimi "Luokka 1c" :numero 3}
 {:lyhyt-nimi "2a" :nimi "Luokka 2a" :numero 4}
 {:lyhyt-nimi "2b" :nimi "Luokka 2b" :numero 5}
 {:lyhyt-nimi "3a" :nimi "Luokka 3a" :numero 6}
 {:lyhyt-nimi "3b" :nimi "Luokka 3b" :numero 7}])

(def kaikki-yllapitoluokat (concat nykyiset-yllapitoluokat vanhat-yllapitoluokat))

(s/def ::yllapitoluokka (s/int-in (apply min (keep :numero kaikki-yllapitoluokat))
                                  (inc (apply max (keep :numero kaikki-yllapitoluokat)))))

(def ^{:doc "Mahdolliset ylläpitoluokat. Nimi kertoo käyttöliittymässä käytetyn
nimen. Numero on YHA:n koodi luokalle joka talletetaan myös Harjan kantaan.
2017 alkaen pyritään käyttämään enää luokkia 1,2 ja 3 (eli numerot 8, 9 ja 10), mutta
taaksenpäinyhteensopivuuden nimissä pidetään vanhatkin luokat koodistossa."}
yllapitoluokat
  (into [] kaikki-yllapitoluokat))


(def ^{:doc "Mäppäys ylläpitoluokan numerosta sen lyhyeen nimeen."}
yllapitoluokkanumero->lyhyt-nimi
  (into {} (map (juxt :numero :lyhyt-nimi)) yllapitoluokat))

(def ^{:doc "Mäppäys ylläpitoluokan numerosta sen kokonimeen."}
yllapitoluokkanumero->nimi
  (into {} (map (juxt :numero :nimi)) yllapitoluokat))

(def ^{:doc "Mäppäys ylläpitoluokan nimestä sen numeroon."}
yllapitoluokkanimi->numero
  (into {} (map (juxt :nimi :numero)) yllapitoluokat))

(def yllapitoluokka-xf
  (map #(assoc % :yllapitoluokka {:nimi (yllapitoluokkanumero->nimi (:yllapitoluokka %))
                                  :lyhyt-nimi (yllapitoluokkanumero->lyhyt-nimi (:yllapitoluokka %))
                                  :numero (:yllapitoluokka %)})))

(def +kohteissa-viallisia-sijainteja+ "viallisia-sijainteja")
(def +viallinen-yllapitokohteen-sijainti+ "viallinen-kohteen-sijainti")
(def +viallinen-yllapitokohdeosan-sijainti+ "viallinen-alikohteen-sijainti")
(def +viallinen-alustatoimenpiteen-sijainti+ "viallinen-alustatoimenpiteen-sijainti")

#?(:clj
   (defn tee-virhe [koodi viesti]
     {:koodi koodi :viesti viesti}))

#?(:clj
   (defn validoi-sijainti [{:keys [aosa aet losa let] :as sijainti}]
     ;; Käytetään täydellä namespacella, jotta voidaan destrukturoida loppuetäisyys (let).
     (clojure.core/let [virhe (fn [viesti] (tee-virhe +viallinen-yllapitokohteen-sijainti+ (format viesti sijainti)))
                        negatiivinen? #(and % (> 0 %))
                        validaattorit [{:validaattori #(nil? aosa) :virhe (virhe "Alkuosa puuttuu. Sijainti: %s")}
                                       {:validaattori #(nil? aet) :virhe (virhe "Alkuetaisyys puuttuu. Sijainti: %s")}
                                       {:validaattori #(nil? losa) :virhe (virhe "Loppuosa puuttuu. Sijainti: %s")}
                                       {:validaattori #(nil? let) :virhe (virhe "Loppuetäisyys puuttuu. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? aosa) :virhe (virhe "Alkuosa ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? aet) :virhe (virhe "Alkuetäisyys ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? losa) :virhe (virhe "Lopppuosa ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(negatiivinen? let) :virhe (virhe "Loppuetäisyys ei saa olla negatiivinen. Sijainti: %s")}
                                       {:validaattori #(> aosa losa) :virhe (virhe "Alkuosa on loppuosaa isompi. Sijainti: %s")}]]
       (keep (fn [{:keys [validaattori virhe]}]
               (when (validaattori) virhe)) validaattorit))))

#?(:clj
   (defn alikohde-kohteen-sisalla? [kohteen-sijainti alikohteen-sijainti]
     (and (<= (:aosa kohteen-sijainti) (:aosa alikohteen-sijainti))
          (or
            (not= (:aosa kohteen-sijainti) (:aosa alikohteen-sijainti))
            (<= (:aet kohteen-sijainti) (:aet alikohteen-sijainti)))
          (>= (:losa kohteen-sijainti) (:losa alikohteen-sijainti))
          (or (not= (:losa kohteen-sijainti) (:losa alikohteen-sijainti))
              (>= (:let kohteen-sijainti) (:let alikohteen-sijainti))))))

#?(:clj
   (defn tarkista-alikohteet-sisaltyvat-kohteeseen [kohde-id kohteen-sijainti alikohteet]
     (mapv (fn [{:keys [tunnus sijainti]}]
             (when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
               (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                          (format "Alikohde (tunnus: %s) ei ole kohteen (id: %s) sisällä. Sijainti: %s." tunnus kohde-id sijainti))))
           alikohteet)))

#?(:clj
   (defn tarkista-alikohteet-tayttavat-kohteen [kohde-id kohteen-sijainti alikohteet]
     (let [ensimmainen (:sijainti (first alikohteet))
           viimeinen (:sijainti (last alikohteet))]
       (when (or (not= (:aosa kohteen-sijainti) (:aosa ensimmainen))
                 (not= (:aet kohteen-sijainti) (:aet ensimmainen))
                 (not= (:losa kohteen-sijainti) (:losa viimeinen))
                 (not= (:let kohteen-sijainti) (:let viimeinen)))
         [(tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                     (format "Alikohteet eivät täytä kohdetta (id: %s)" kohde-id))]))))

#?(:clj
   (defn tarkista-alikohteet-muodostavat-yhtenaisen-osuuden [alikohteet]
     (let [lisaa-virhe (fn [edellinen seuraava]
                         (conj
                           (:virheet edellinen)
                           (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                                      (format "Alikohteet (tunnus: %s ja tunnus: %s) eivät muodosta yhteistä osuutta"
                                              (:tunnus (:edellinen edellinen))
                                              (:tunnus seuraava)))))
           seuraava-jatkaa-edellista? (fn [seuraava edellinen]
                                        (and
                                          (= (get-in edellinen [:edellinen :sijainti :losa]) (:aosa (:sijainti seuraava)))
                                          (= (get-in edellinen [:edellinen :sijainti :let]) (:aet (:sijainti seuraava)))))]
       (:virheet
         (reduce
           (fn [edellinen seuraava]
             (if (seuraava-jatkaa-edellista? seuraava edellinen)
               (assoc edellinen :edellinen seuraava)
               {:edellinen seuraava
                :virheet (lisaa-virhe edellinen seuraava)}))
           {:virheet [] :edellinen (first alikohteet)}
           (rest alikohteet))))))

#?(:clj
   (defn tarkista-alikohteiden-sijainnit [alikohteet]
     (flatten (mapv #(validoi-sijainti (:sijainti %)) alikohteet))))

#?(:clj
   (defn validoi-alikohteet [kohde-id kohteen-sijainti alikohteet]
     (when alikohteet
       (concat
         (tarkista-alikohteiden-sijainnit alikohteet)
         (tarkista-alikohteet-sisaltyvat-kohteeseen kohde-id kohteen-sijainti alikohteet)
         (tarkista-alikohteet-tayttavat-kohteen kohde-id kohteen-sijainti alikohteet)
         (tarkista-alikohteet-muodostavat-yhtenaisen-osuuden alikohteet)))))

#?(:clj
   (defn tarkista-kohteen-ja-alikohteiden-sijannit
     "Tekee yksinkertaisen tarkastuksen, jolloin kohde on validi ja alikohteet ovat sen sisällä ja muodostavat yhteinäisen
     kokonaisuuden. Varsinainen validius tieverkon kannalta täytyy tarkistaa erikseen tietokantaa vasten."
     [kohde-id kohteen-sijainti alikohteet]

     (let [alikohteet (when alikohteet (sort-by (juxt #(get-in % [:sijainti :aosa]) #(get-in % [:sijainti :aet])) alikohteet))
           virheet (remove nil? (concat
                                  (validoi-sijainti kohteen-sijainti)
                                  (validoi-alikohteet kohde-id kohteen-sijainti alikohteet)))]

       (when (not (empty? virheet))
         (virheet/heita-poikkeus +kohteissa-viallisia-sijainteja+ virheet)))))

#?(:clj
   (defn validoi-alustatoimenpide [kohde-id kohteen-sijainti sijainti]
     (when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
       [(tee-virhe +viallinen-alustatoimenpiteen-sijainti+
                   (format "Alustatoimenpide ei ole kohteen (id: %s) sisällä." kohde-id))])))

#?(:clj (defn tarkista-alustatoimenpiteiden-sijainnit
          "Varmistaa että kaikkien alustatoimenpiteiden sijainnit ovat kohteen sijainnin sisällä"
          [kohde-id kohteen-sijainti alustatoimet]
          (let [virheet
                (flatten
                  (keep (fn [{:keys [sijainti]}]
                          (let [kohteenvirheet
                                (concat
                                  (validoi-sijainti sijainti)
                                  (validoi-alustatoimenpide kohde-id kohteen-sijainti sijainti))]
                            kohteenvirheet))
                        alustatoimet))]
            (when (not (empty? virheet))
              (virheet/heita-poikkeus +kohteissa-viallisia-sijainteja+ virheet)))))


#?(:clj
   (defn yllapitokohteen-tarkka-tila [yllapitokohde]
     (cond
       (and (:kohde-valmispvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:kohde-valmispvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :kohde-valmis

       (and (:tiemerkinta-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:tiemerkinta-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :tiemerkinta-valmis

       (and (:tiemerkinta-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:tiemerkinta-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :tiemerkinta-aloitettu

       (and (:paallystys-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paallystys-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paallystys-valmis

       (and (:paallystys-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paallystys-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paallystys-aloitettu

       (and (:paikkaus-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paikkaus-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paikkaus-valmis

       (and (:paikkaus-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paikkaus-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paikkaus-aloitettu

       (and (:kohde-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:kohde-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :kohde-aloitettu

       :default
       :ei-aloitettu)))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :ei-aloitettu "Ei aloitettu"
    :kohde-aloitettu "Kohde aloitettu"
    :paallystys-aloitettu "Päällystys aloitettu"
    :paallystys-valmis "Päällystys valmis"
    :paikkaus-aloitettu "Paikkaus aloitettu"
    :paikkaus-valmis "Paikkaus valmis"
    :tiemerkinta-aloitettu "Tiemerkintä aloitettu"
    :tiemerkinta-valmis "Tiemerkintä valmis"
    :kohde-valmis "Kohde valmis"
    "Ei tiedossa"))

(defn yllapitokohteen-tila-kartalla [tarkka-tila]
  (case tarkka-tila
    :ei-aloitettu :ei-aloitettu
    :kohde-aloitettu :kesken
    :paallystys-aloitettu :kesken
    :paallystys-valmis :kesken
    :paikkaus-aloitettu :kesken
    :paikkaus-valmis :kesken
    :tiemerkinta-aloitettu :kesken
    :tiemerkinta-valmis :kesken
    :kohde-valmis :valmis))

(defn kuvaile-kohteen-tila-kartalla [tila]
  (case tila
    :valmis "Valmis"
    :kesken "Kesken"
    :ei-aloitettu "Ei aloitettu"
    "Ei tiedossa"))

(def yllapitokohde-kartalle-xf
  ;; Ylläpitokohde näytetään kartalla 'kohdeosina'.
  ;; Tämä transducer olettaa saavansa vectorin ylläpitokohteita ja palauttaa
  ;; ylläpitokohteiden kohdeosat valmiina näytettäväksi kartalle.
  ;; Palautuneilla kohdeosilla on pääkohteen tiedot :yllapitokohde avaimen takana.
  (comp
    (mapcat (fn [kohde]
              (keep (fn [kohdeosa]
                      (assoc kohdeosa :yllapitokohde (dissoc kohde :kohdeosat)
                                      :tyyppi-kartalla (:yllapitokohdetyotyyppi kohde)
                                      :tila (:tila kohde)
                                      :yllapitokohde-id (:id kohde)))
                    (:kohdeosat kohde))))
    (keep #(and (:sijainti %) %))))

(defn- yllapitokohteen-jarjestys
  [kohde]
  ((juxt :kohdenumero
         :tie :tr-numero :tienumero
         :aosa :tr-alkuosa
         :aet :tr-alkuetaisyys) kohde))

(defn- jarjesta-yllapitokohteet*
  [kohteet]
  (sort-by yllapitokohteen-jarjestys kohteet))

(defn jarjesta-yllapitokohteet [yllapitokohteet]
  (let [kohteet-kohdenumerolla (filter #(not (str/blank? (:kohdenumero %))) yllapitokohteet)
        kohteet-ilman-kohdenumeroa (filter #(str/blank? (:kohdenumero %)) yllapitokohteet)]
    (vec (concat (jarjesta-yllapitokohteet* kohteet-kohdenumerolla)
                 (tr-domain/jarjesta-tiet kohteet-ilman-kohdenumeroa)))))

(defn yllapitokohteen-kokonaishinta [{:keys [sopimuksen-mukaiset-tyot maaramuutokset toteutunut-hinta
                                             bitumi-indeksi arvonvahennykset kaasuindeksi sakot-ja-bonukset]}]
  (reduce + 0 (remove nil? [sopimuksen-mukaiset-tyot ;; Sama kuin kohteen tarjoushinta
                            maaramuutokset ;; Kohteen määrämuutokset summattuna valmiiksi yhteen
                            arvonvahennykset ;; Sama kuin arvonmuutokset
                            sakot-ja-bonukset ;; Sakot ja bonukset summattuna valmiiksi yhteen.
                            ;; HUOM. sillä oletuksella, että sakot ovat miinusta ja bonukset plussaa.
                            bitumi-indeksi
                            kaasuindeksi
                            toteutunut-hinta ;; Kohteen toteutunut hinta (vain paikkauskohteilla)
                            ])))

(defn yllapitokohde-tekstina
  "Näyttää ylläpitokohteen kohdenumeron ja nimen.

  Optiot on map, jossa voi olla arvot:
  osoite              Kohteen tierekisteriosoite.
                      Näytetään sulkeissa kohteen tietojen perässä sulkeissa, jos löytyy."
  ([kohde] (yllapitokohde-tekstina kohde {}))
  ([kohde optiot]
   (let [kohdenumero (or (:kohdenumero kohde) (:numero kohde) (:yllapitokohdenumero kohde))
         nimi (or (:nimi kohde) (:yllapitokohdenimi kohde))
         osoite (when-let [osoite (:osoite optiot)]
                  (let [tr-osoite (tr-domain/tierekisteriosoite-tekstina osoite {:teksti-ei-tr-osoitetta? false
                                                                                 :teksti-tie? false})]
                    (when-not (empty? tr-osoite)
                      (str " (" tr-osoite ")"))))]
     (str kohdenumero " " nimi osoite))))

(defn lihavoi-vasta-muokatut [rivit]
  (let [viikko-sitten (pvm/paivaa-sitten 7)]
    (map (fn [{:keys [muokattu aikataulu-muokattu] :as rivi}]
           (assoc rivi :lihavoi
                  (or (and muokattu (pvm/ennen? viikko-sitten muokattu))
                      (and aikataulu-muokattu (pvm/ennen? viikko-sitten aikataulu-muokattu)))))
         rivit)))
