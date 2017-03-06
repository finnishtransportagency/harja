(ns harja.domain.yllapitokohteet
  "Ylläpitokohteiden yhteisiä apureita"
  #?(:clj
     (:require [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
               [clojure.spec :as s]
       #?@(:clj [
               [clojure.future :refer :all]])))
  #?(:cljs
     (:require [cljs.spec :as s])))

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


(defn yllapitokohteen-tarkka-tila [yllapitokohde]
  ;; Järjestys on tärkeä, koska nämä menee yleensä tässä aikajärjestyksessä.
  (cond (:kohde-valmispvm yllapitokohde)
        :kohde-valmis

        (:tiemerkinta-loppupvm yllapitokohde)
        :tiemerkinta-valmis

        (:tiemerkinta-alkupvm yllapitokohde)
        :tiemerkinta-aloitettu

        (:paallystys-loppupvm yllapitokohde)
        :paallystys-valmis

        (:paallystys-alkupvm yllapitokohde)
        :paallystys-aloitettu

        (:paikkaus-loppupvm yllapitokohde)
        :paikkaus-valmis

        (:paikkaus-alkupvm yllapitokohde)
        :paikkaus-aloitettu

        (:kohde-alkupvm yllapitokohde)
        :kohde-aloitettu

        :default
        :ei-aloitettu))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :kohde-aloitettu "Kohde aloitettu"
    :paallystys-aloitettu "Päällystys aloitettu"
    :paallystys-valmis "Päällystys valmis"
    :tiemerkinta-aloitettu "Tiemerkintä aloitettu"
    :tiemerkinta-valmis "Tiemerkintä valmis"
    :kohde-valmis "Kohde valmis"
    :ei-aloitettu "Ei aloitettu"
    "Ei tiedossa"))

(defn yllapitokohteen-tila-kartalla [yllapitokohde]
  ;; Järjestys on tärkeä, koska nämä menee yleensä tässä aikajärjestyksessä.
  (cond (:kohde-valmispvm yllapitokohde)
        :valmis

        (:tiemerkinta-loppupvm yllapitokohde)
        :valmis

        (:tiemerkinta-alkupvm yllapitokohde)
        :valmis

        (:paallystys-loppupvm yllapitokohde)
        :valmis

        (:paallystys-alkupvm yllapitokohde)
        :aloitettu

        (:paikkaus-loppupvm yllapitokohde)
        :aloitettu

        (:paikkaus-alkupvm yllapitokohde)
        :aloitettu

        (:kohde-alkupvm yllapitokohde)
        :aloitettu

        :default
        nil))

(defn kuvaile-kohteen-tila-kartalla [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))