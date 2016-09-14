(ns taulun-partitiointi
  (:require [clojure.string :as str]))

(def kvartaalit [["-01-01" "-04-01"]
                 ["-04-01" "-07-01"]
                 ["-07-01" "-10-01"]
                 ["-10-01"]])

(defn aika-partitiot [taulu aika vuodet vuoden-osat osa-prefix]
  (into [{:taulu (str taulu "_ennen_" (first vuodet))
          :inherits taulu
          :ehto (str aika " < '" (first vuodet) "-01-01'::date")}]
        (mapcat
         (fn [v]
           (map-indexed
            (fn [i [alku-pvm loppu-pvm]]
              {:taulu (str taulu "_" v "_" osa-prefix (inc i))
               :inherits taulu
               :ehto (str aika " >= '"v alku-pvm"'::date AND "
                          aika " < '" (if loppu-pvm
                                        (str v loppu-pvm)
                                        (str (inc v) "-01-01")) "'::date")})
            vuoden-osat))
         vuodet)))

(defn luo-taulumaaritykset [taulut]
  (str/join
   "\n"
   (for [{:keys [taulu inherits ehto]} taulut]
     (str "CREATE TABLE " taulu " ( CHECK( " ehto ") ) INHERITS (" inherits ");"))))

(defn luo-indeksimaaritykset [taulut kentta uniikki?]
  (str/join
   "\n"
   (map #(str "CREATE " (when uniikki? "UNIQUE ")
              "INDEX " (:taulu %) "_" (str/replace kentta #"," "_") "_idx"
              " ON " (:taulu %) " (" kentta ");")
        taulut)))

(defn luo-insert-trigger [taulut arvo-nimi arvo-tyyppi]
  (let [taulu (:inherits (first taulut))]
    (str "CREATE OR REPLACE FUNCTION " taulu "_insert() RETURNS trigger AS $$\n"
         "DECLARE\n"
         "  " arvo-nimi " " arvo-tyyppi ";\n"
         "BEGIN\n"
         "  " arvo-nimi " := NEW." arvo-nimi ";\n"
         (str/join
          "\n"
          (map-indexed
           (fn [i {:keys [taulu ehto]}]
             (str (if (zero? i)
                    "  IF "
                    "  ELSIF ")
                  ehto " THEN \n"
                  "    INSERT INTO " taulu " VALUES (NEW.*);"))
           taulut))
         "  ELSE\n"
         "    RAISE EXCEPTION 'Taululle " taulu " ei löydy insert ehtoa, korjaa " taulu "_insert() sproc!';\n"
         "  END IF;\n"
         "  RETURN NULL;\n"
         "END;\n"
         "$$ LANGUAGE plpgsql;\n\n"

         "CREATE TRIGGER tg_" taulu "_insert\n"
         "BEFORE INSERT ON " taulu "\n"
         "FOR EACH ROW EXECUTE PROCEDURE " taulu "_insert();\n";

         )
    ))

(defn luo-tarkastus-partitiot []
  (let [partitiot (aika-partitiot "tarkastus" "aika" (range 2015 2021) kvartaalit "q")]
    (str
     "-- Luo taulut kvartaaleittain\n"
     (luo-taulumaaritykset partitiot)
     "\n\n-- Luo indeksit osille\n"
     (luo-indeksimaaritykset partitiot "id" true) "\n"
     (luo-indeksimaaritykset partitiot "urakka" false)
     (luo-indeksimaaritykset partitiot "ulkoinen_id,luoja" true)
     "\n\n-- Luo insert trigger\n"
     (luo-insert-trigger partitiot "aika" "date")

     )))

(spit "tark_part.sql" (luo-tarkastus-partitiot))
