(ns harja.domain.yllapitokohteet
  "Ylläpitokohteiden yhteisiä apureita"
  (:use [slingshot.slingshot :only [throw+]])
  (:require [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

(def +kohteissa-viallisia-sijainteja+ "viallisia-sijainteja")
(def +viallinen-yllapitokohteen-sijainti+ "viallinen-kohteen-sijainti")
(def +viallinen-yllapitokohdeosan-sijainti+ "viallinen-alikohteen-sijainti")
(def +viallinen-alustatoimenpiteen-sijainti+ "viallinen-alustatoimenpiteen-sijainti")

(defn tee-virhe [koodi viesti]
  {:koodi koodi :viesti viesti})

(defn validoi-sijainti [{:keys [aosa aet losa let] :as sijainti}]
  ;; Käytetään täydellä namespacella, jotta voidaan destrukturoida loppuetäisyys (let)
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
    (keep #(when ((:validaattori %)) (:virhe %)) validaattorit)))

(defn alikohde-kohteen-sisalla? [kohteen-sijainti alikohteen-sijainti]
  (and (<= (:aosa kohteen-sijainti) (:aosa alikohteen-sijainti))
       (<= (:aet kohteen-sijainti) (:aet alikohteen-sijainti))
       (>= (:losa kohteen-sijainti) (:losa alikohteen-sijainti))
       (>= (:let kohteen-sijainti) (:let alikohteen-sijainti))))

(defn tarkista-alikohteet-sisaltyvat-kohteeseen [kohde-id kohteen-sijainti alikohteet]
  (mapv (fn [{:keys [tunnus sijainti]}]
          (when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
            (tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                       (format "Alikohde (tunnus: %s) ei ole kohteen (id: %s) sisällä. Sijainti: %s." tunnus kohde-id sijainti))))
        alikohteet))

(defn tarkista-alikohteet-tayttavat-kohteen [kohde-id kohteen-sijainti alikohteet]
  (let [ensimmainen (:sijainti (first alikohteet))
        viimeinen (:sijainti (last alikohteet))]
    (when (or (not= (:aosa kohteen-sijainti) (:aosa ensimmainen))
              (not= (:aet kohteen-sijainti) (:aet ensimmainen))
              (not= (:losa kohteen-sijainti) (:losa viimeinen))
              (not= (:let kohteen-sijainti) (:let viimeinen)))
      [(tee-virhe +viallinen-yllapitokohdeosan-sijainti+
                  (format "Alikohteet eivät täytä kohdetta (id: %s)" kohde-id))])))

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
             :virheet   (lisaa-virhe edellinen seuraava)}))
        {:virheet [] :edellinen (first alikohteet)}
        (rest alikohteet)))))

(defn tarkista-alikohteiden-sijainnit [alikohteet]
  (flatten (mapv #(validoi-sijainti (:sijainti %)) alikohteet)))

(defn validoi-alikohteet [kohde-id kohteen-sijainti alikohteet]
  (when alikohteet
    (concat
      (tarkista-alikohteiden-sijainnit alikohteet)
      (tarkista-alikohteet-sisaltyvat-kohteeseen kohde-id kohteen-sijainti alikohteet)
      (tarkista-alikohteet-tayttavat-kohteen kohde-id kohteen-sijainti alikohteet)
      (tarkista-alikohteet-muodostavat-yhtenaisen-osuuden alikohteet))))

(defn tarkista-kohteen-ja-alikohteiden-sijannit
  "Tekee yksinkertaisen tarkastuksen, jolloin kohde on validi ja alikohteet ovat sen sisällä ja muodostavat yhteinäisen
  kokonaisuuden. Varsinainen validius tieverkon kannalta täytyy tarkistaa erikseen tietokantaa vasten."
  [kohde-id kohteen-sijainti alikohteet]

  (let [alikohteet (when alikohteet (sort-by (juxt #(get-in % [:sijainti :aosa]) #(get-in % [:sijainti :aet])) alikohteet))
        virheet (remove nil? (concat
                               (validoi-sijainti kohteen-sijainti)
                               (validoi-alikohteet kohde-id kohteen-sijainti alikohteet)))]

    (when (not (empty? virheet))
      (virheet/heita-poikkeus +kohteissa-viallisia-sijainteja+ virheet))))

(defn tarkista-alustatoimenpiteiden-sijainnit
  "Varmistaa että kaikkien alustatoimenpiteiden sijainnit ovat kohteen sijainnin sisällä"
  [kohde-id kohteen-sijainti alustatoimet]
  (let [virheet (flatten
                  (keep (fn [{:keys [sijainti]}]
                          (concat
                            (validoi-sijainti sijainti)
                            [(when (not (alikohde-kohteen-sisalla? kohteen-sijainti sijainti))
                               (tee-virhe +viallinen-alustatoimenpiteen-sijainti+
                                          (format "Alustatoimenpide ei ole kohteen (id: %s) sisällä." kohde-id)))]))
                        alustatoimet))]
    (when (not (empty? virheet))
      (virheet/heita-poikkeus +kohteissa-viallisia-sijainteja+ virheet))))
