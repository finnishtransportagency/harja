(ns harja.tyokalut.threading)

(defn- expand-operation [pred-sym value-sym [operation & operations]]
  (let [next-op-form (if (list? operation)
                       (concat (list (first operation)
                                     value-sym)
                               (rest operation))
                       (list operation value-sym))]
    `(let [~value-sym ~next-op-form]
       (if-not (~pred-sym ~value-sym)
         ~value-sym
         ~(if (empty? operations)
            value-sym
            (expand-operation pred-sym value-sym operations))))))

(defmacro when-> [pred value & operations]
  (let [pred-sym (gensym "PRED")
        value-sym (gensym "VAL")]
    `(let [~pred-sym ~pred
           ~value-sym ~value]
       ~(expand-operation pred-sym value-sym operations))))
