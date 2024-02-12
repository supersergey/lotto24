package com.example.demo.controller

import com.example.demo.exception.InsufficientAccountBalanceException
import com.example.demo.exception.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class RestExceptionsHandler {
    @ExceptionHandler(InsufficientAccountBalanceException::class)
    fun handleInsufficientAccountBalanceException(ex: InsufficientAccountBalanceException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.message)

    @ExceptionHandler(NotFoundException::class)
    fun handleAccountNotFoundException(ex: NotFoundException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.message)

}